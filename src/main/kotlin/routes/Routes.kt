package routes

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.http.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.trace.Tracer

data class Move (val description: String ="", val type: String = "")

val tracer = getTracer("Kotlin Test")

fun getTracer(service: String): Tracer {
    return OpenTelemetrySdk.getTracerFactory().get("KotlinTracer")
}
fun createRoutes(){

    val handler = MoveRequestHandler(tracer)

    val app = Javalin.create { config ->
        config.defaultContentType = "application/json"
        config.dynamicGzip = true
        config.contextPath = "/api/v1"
    }.routes{
       get("/moves/:move"){ctx: Context -> ctx.json(handler.getMoveByName(ctx))}
        get("/moves/"){ctx: Context -> ctx.json(handler.getAllMoves())}
    }

    app.before { ctx ->
        //Extract Context here
    }

    app.after{ ctx->
//        tracer.activeSpan().finish()

        //Close our span and inject context
    }

    app.error(404) { ctx->
        ctx.json("404, route doesn't exist. Try http://localhost:1991/api/v1/moves")
    }.start(1991)
}

class MoveRequestHandler(tracer: Tracer) {

    private val moveDAO = MoveDAO(tracer)

    fun getMoveByName(ctx: Context):Move {

        val moveName = ctx.pathParam("move")
        return moveDAO.getMoveByName(moveName)
    }

    fun getAllMoves(): HashMap<String, Move> {

        return moveDAO.moves
    }

}

class MoveDAO (tracer: Tracer)  {

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
