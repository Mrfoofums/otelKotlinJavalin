package routes

import io.grpc.ManagedChannelBuilder
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.http.Context
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter
import io.opentelemetry.exporters.logging.LoggingSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.Tracer
import kotlin.collections.HashMap


data class Move (val description: String ="", val type: String = "")

val tracer = getTracer("Kotlin Test")

fun getTracer(service: String): Tracer {
    val tracer = OpenTelemetrySdk.getTracerProvider().get("Kotlin Example")
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

    val logProcessor: SpanProcessor = SimpleSpansProcessor.newBuilder(LoggingSpanExporter()).build()

    OpenTelemetrySdk.getTracerProvider().addSpanProcessor(logProcessor)
    OpenTelemetrySdk.getTracerProvider().addSpanProcessor(jaegerProcessor)

    return tracer
}

fun createRoutes(){

    var beforeSpan: Span = tracer.currentSpan // Should be nothing when we bootstrap our application
    val handler = MoveRequestHandler()

    val app = Javalin.create { config ->
        config.defaultContentType = "application/json"
        config.dynamicGzip = true
        config.contextPath = "/api/v1"
    }.routes{
       get("/moves/:move"){ctx: Context ->
               ctx.json(handler.getMoveByName(ctx))

       }
        get("/moves/"){ctx: Context ->

            val span = tracer.spanBuilder("beforeSpan").startSpan()
            try {
                ctx.json(handler.getAllMoves(span))
            }
            finally {
                span.end()
            }
        }
    }

    app.before { ctx ->
       beforeSpan = tracer.spanBuilder(ctx.fullUrl()).startSpan().also {
            it.setAttribute("context","before")
           tracer.withSpan(it)
        }

    }

    app.after{
        beforeSpan.end()
    }

    app.error(404) { ctx->
        ctx.json("404, route doesn't exist. Try http://localhost:1991/api/v1/moves")
    }.start(1991)
}

class MoveRequestHandler{

    private val moveDAO = MoveDAO()

    fun getMoveByName(ctx: Context):Move {
        val moveName = ctx.pathParam("move")

        tracer.spanBuilder("getMoveByNameSpanHandler").setParent(tracer.currentSpan).startSpan().also {
            tracer.withSpan(it)
            it.setAttribute("move", moveName)
            it.end()

            return moveDAO.getMoveByName(moveName)
        }

    }

    fun getAllMoves(parentSpan: Span): HashMap<String, Move> {
        tracer.spanBuilder("getAllMovesSpan").setParent(parentSpan).startSpan().also {
            tracer.withSpan(it)
            it.setAttribute("move", "allMoves")
            it.end()

            return  moveDAO.moves
        }

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
        println("ParnetSpan ID passed into GetMoveByNameSpan id: ${tracer.currentSpan.context.spanId}")
        tracer.spanBuilder("getMoveByNameDAOSpan").setParent(tracer.currentSpan).startSpan().also {
            it.setAttribute("move", moveName)
            it.end()

            return moves.getValue(moveName)

        }
    }
}
