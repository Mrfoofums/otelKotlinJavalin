# Kotlin and LightStep
###### How to instrument a Kotlin app with LightStep/Opentracing


## Overview
Before we get started we should probably cover some basics. Distributed Tracing refers to the concept of collecting trace data from applications in software architectures that are microservice oriented or similar. It provides information about the state of complex architectures in ways previously impossible. Standard API implementations to get up and running are being developed by the OpenTelemetry project(https://opentelemetry.io). Go check it out!

LightStep is a SaSS platform that provides best in industry analysis and insight into distributed trace data. (https://lightstep.com/) Go check it out!

Cool.

## Admin Work
First, make sure you have a LightStep account created and know where your access token is. Instructions here ->(https://docs.lightstep.com/docs/lightstep-tracing-quickstart)

Anyhow..

## Set Up
We are going to instrument a Kotlin REST service built using the Javalin framework. Javalin is lightweight, read up on it here. (https://javalin.io/) I’m a dancer, so my service serves up dance moves.

Here is our starting code:
```` kotlin
package routes

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.http.Context

data class Move (val description: String ="", val type: String = "")

fun createRoutes(){

   val handler = MoveRequestHandler()

   val app = Javalin.create { config ->
       config.defaultContentType = "application/json"
       config.dynamicGzip = true
       config.contextPath = "/api/v1"
   }.routes{
      get("/moves/:move"){ctx: Context -> ctx.json(handler.getMoveByName(ctx))}
       get("/moves/"){ctx: Context -> ctx.json(handler.getAllMoves())}
   }

   app.before { ctx ->
   }

   app.after{ ctx->
   }

   app.error(404) { ctx->
       ctx.json("404, route doesn't exist. Try http://localhost:1991/api/v1/moves")
   }.start(1991)
}

class MoveRequestHandler {

   private val moveDAO = MoveDAO()

   fun getMoveByName(ctx: Context):Move {
       val moveName = ctx.pathParam("move")
       return moveDAO.getMoveByName(moveName)
   }

   fun getAllMoves(): HashMap<String, Move> {
       return moveDAO.moves
   }

}

class MoveDAO {
    val moves = hashMapOf(
       "windmill" to Move(
           "A classic bboy move where the dancer spins around the crown of their head with their legs out",
           "Power Move"
       ),
       "flare" to Move("A classic power move where the dancer throws both legs out in a circle similar to gymnast circles, but with the legs open", "Air Power"),
       "toprock" to Move("The Top Rock is all movement where the breaker is still standing. Set's are typically started with top rock.", "Rocking")
   )
   fun getMoveByName(moveName: String): Move {

       return moves.getValue(moveName)
   }
}
````


## Dependencies
Add the following otel and Okttp dependencies

Just copy these into your gradle file.
```` gradle
    compile "io.opentelemetry:opentelemetry-api:0.2.0"
    compile "io.opentelemetry:opentelemetry-sdk:0.2.0"
    compile "io.opentelemetry:opentelemetry-exporters-logging:0.2.0"
    compile "io.opentelemetry:opentelemetry-exporters-jaeger:0.2.0"
    compile "io.grpc:grpc-protobuf:1.27.2"
    compile "io.grpc:grpc-netty:1.27.2"

    compile "com.squareup.okhttp3:okhttp:4.4.0"
````

## Create an init function for a global tracer
Kotlin is interesting in that we can create functions outside of classes and use them loosely. If you know how Kotlin in the JVM works, you know what happens once it compiles, but for now, lets throw this at the top of our file.

We are initializing and configuring our tracer all at once. You might separate these concerns in your application.

```` kotlin
val tracer = getTracer("Kotlin Test")

fun getTracer(service: String): Tracer {
    val tracer = OpenTelemetrySdk.getTracerFactory().get("KotlinTracer")
    val jaegerProcessor: SpanProcessor = SimpleSpansProcessor.newBuilder(
        JaegerGrpcSpanExporter.newBuilder()
            .setServiceName(service)
            .setChannel(
                ManagedChannelBuilder.forAddress(
                    "localhost", 14250
                ).usePlaintext().build()
            )
            .build()
    ).build()

    val logProcessor: SpanProcessor = SimpleSpansProcessor.newBuilder(LoggingExporter()).build()

    OpenTelemetrySdk.getTracerFactory().addSpanProcessor(logProcessor)
    OpenTelemetrySdk.getTracerFactory().addSpanProcessor(jaegerProcessor)

    return tracer
}
````

## Instrumentation - Adding the Tracer
Javalin has some cool route level handlers we can use such as before() and after(). Add the following to the createRoutes() function so we can create spans and send data to LightStep everytime an endpoint is hit.
```` kotlin
   app.before {
           val beforeSpan = tracer.spanBuilder(it.fullUrl()).startSpan().also {
               it.setAttribute("context","before")
           }
           tracer.withSpan(beforeSpan)
       }
   
       app.after{
           val afterSpan = tracer.spanBuilder("afterSpan").setParent(tracer.currentSpan).startSpan().also {
               it.setAttribute("post.processing", "none")
           }
           afterSpan.end()
       }
````
## Tracing our controllers and DAO
We need to trace the specific handlers and DAO for this mini service. 

Handler/controller
```` kotlin
class MoveRequestHandler{

    private val moveDAO = MoveDAO()

    fun getMoveByName(ctx: Context):Move {
        val moveName = ctx.pathParam("move")
        tracer.spanBuilder("getMoveSPan").setParent(tracer.currentSpan).startSpan().also {
            it.setAttribute("move", moveName)
            it.setAttribute("content-type",ctx.contentType())
            tracer.withSpan(it)
        }
        return moveDAO.getMoveByName(moveName)
    }

    fun getAllMoves(): HashMap<String, Move> {
        tracer.spanBuilder("getAllMovesSpan").setParent(tracer.currentSpan).startSpan().also {
            it.setAttribute("move", "allMoves")
            tracer.withSpan(it)
        }
        return moveDAO.moves
    }

}
````

## DAO
```` kotlin
class MoveDAO   {

     val moves = hashMapOf(
        "windmill" to Move(
            "A classic bboy move where the dancer spins around the crown of their head with their legs out",
            "Power Move"
        ),
        "flare" to Move("A classic power move where the dancer throws both legs out in a circle similar to gymnast circles, but with the legs open", "Air Power"),
        "toprock" to Move("The Top Rock is all movement where the breaker is still standing. Set's are typically started with top rock.", "Rocking")
    )

    fun getMoveByName(moveName: String): Move {
        tracer.spanBuilder("getMoveByNameDAOSpan").setParent(tracer.currentSpan).startSpan().also {
            it.setAttribute("move", moveName)
            tracer.withSpan(it)
        }
        return moves.getValue(moveName)
    }
}
````

## And we are done!

Complete example source here:
https://github.com/Mrfoofums/otelKotlinJavalin

## Full App Code
Typically we would’t put request controllers and DAO in the same file, but I did here for the sake of example.
package routes

```` kotlin
package routes

import io.grpc.ManagedChannelBuilder
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.http.Context
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter
import io.opentelemetry.exporters.logging.LoggingExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor
import io.opentelemetry.trace.Tracer


data class Move (val description: String ="", val type: String = "")

val tracer = getTracer("Kotlin Test")

fun getTracer(service: String): Tracer {
    val tracer = OpenTelemetrySdk.getTracerFactory().get("KotlinTracer")
    val jaegerProcessor: SpanProcessor = SimpleSpansProcessor.newBuilder(
        JaegerGrpcSpanExporter.newBuilder()
            .setServiceName(service)
            .setChannel(
                ManagedChannelBuilder.forAddress(
                    "localhost", 14250
                ).usePlaintext().build()
            )
            .build()
    ).build()

    val logProcessor: SpanProcessor = SimpleSpansProcessor.newBuilder(LoggingExporter()).build()

    OpenTelemetrySdk.getTracerFactory().addSpanProcessor(logProcessor)
    OpenTelemetrySdk.getTracerFactory().addSpanProcessor(jaegerProcessor)

    return tracer
}

fun createRoutes(){

    val handler = MoveRequestHandler()

    val app = Javalin.create { config ->
        config.defaultContentType = "application/json"
        config.dynamicGzip = true
        config.contextPath = "/api/v1"
    }.routes{
       get("/moves/:move"){ctx: Context -> ctx.json(handler.getMoveByName(ctx))}
        get("/moves/"){ctx: Context -> ctx.json(handler.getAllMoves())}
    }

    app.before {
        val beforeSpan = tracer.spanBuilder(it.fullUrl()).startSpan().also {
            it.setAttribute("context","before")
        }
        tracer.withSpan(beforeSpan)
    }

    app.after{
        val afterSpan = tracer.spanBuilder("afterSpan").setParent(tracer.currentSpan).startSpan().also {
            it.setAttribute("post.processing", "none")
        }
        afterSpan.end()
    }

    app.error(404) { ctx->
        val errorSpan = tracer.spanBuilder("afterSpan").setParent(tracer.currentSpan).startSpan().also {
            it.setAttribute("error",true)
            it.addEvent(ctx.toString())
        }
        errorSpan.end()
        ctx.json("404, route doesn't exist. Try http://localhost:1991/api/v1/moves")
    }.start(1991)
}

class MoveRequestHandler{

    private val moveDAO = MoveDAO()

    fun getMoveByName(ctx: Context):Move {
        val moveName = ctx.pathParam("move")
        tracer.spanBuilder("getMoveSPan").setParent(tracer.currentSpan).startSpan().also {
            it.setAttribute("move", moveName)
            it.setAttribute("content-type",ctx.contentType())
            tracer.withSpan(it)
        }
        return moveDAO.getMoveByName(moveName)
    }

    fun getAllMoves(): HashMap<String, Move> {
        tracer.spanBuilder("getAllMovesSpan").setParent(tracer.currentSpan).startSpan().also {
            it.setAttribute("move", "allMoves")
            tracer.withSpan(it)
        }
        return moveDAO.moves
    }

}

class MoveDAO   {

     val moves = hashMapOf(
        "windmill" to Move(
            "A classic bboy move where the dancer spins around the crown of their head with their legs out",
            "Power Move"
        ),
        "flare" to Move("A classic power move where the dancer throws both legs out in a circle similar to gymnast circles, but with the legs open", "Air Power"),
        "toprock" to Move("The Top Rock is all movement where the breaker is still standing. Set's are typically started with top rock.", "Rocking")
    )

    fun getMoveByName(moveName: String): Move {
        tracer.spanBuilder("getMoveByNameDAOSpan").setParent(tracer.currentSpan).startSpan().also {
            it.setAttribute("move", moveName)
            tracer.withSpan(it)
        }
        return moves.getValue(moveName)
    }
}

````
# See your traces
Run the Jaeger all in one docker image here and navigate to localhost://16686
```
docker run -d -p 6831:6831/udp -p 16686:16686 -p 14250:14250
jaegertracing/all-in-one:latest
```







