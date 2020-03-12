package routes

import com.lightstep.tracer.jre.JRETracer
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.http.Context

data class Move (val description: String ="", val type: String = "")

val tracer = getTracer("Kotlin Test")

fun getTracer(service: String): JRETracer {
    return JRETracer(
        com.lightstep.tracer.shared.Options.OptionsBuilder()
            .withAccessToken("6how6J0/2q4Dqxo6VYi6CKXwQJARSmcvydZYNIpti97UAsyjiCbH1aKTp3VoealzQouMtY0FA0xuwvDQhYJFU87fXMngEWngSxDKCSsI")
            .withClockSkewCorrection(false)
            .build()
    )
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
        val span = tracer.buildSpan("api entered").start()
        tracer.scopeManager().activate(span)
        span.setTag("api","entry")
    }

    app.after{ ctx->
        tracer.activeSpan().finish()
    }

    app.error(404) { ctx->
        ctx.json("404, route doesn't exist. Try http://localhost:1991/api/v1/moves")
    }.start(1991)
}

class MoveRequestHandler(tracer: JRETracer) {

    private val moveDAO = MoveDAO(tracer)

    fun getMoveByName(ctx: Context):Move {
        val span = tracer.buildSpan("getMoveByNameHANDLER").start()
        span.setTag("controller","getmovebyname")

        tracer.scopeManager().activate(span)
        val moveName = ctx.pathParam("move")
        return moveDAO.getMoveByName(moveName)
    }

    fun getAllMoves(): HashMap<String, Move> {
        val span = tracer.buildSpan("getAllMovesHandler").start()
        span.setTag("controller","getallmoves")
        tracer.scopeManager().activate(span)
        return moveDAO.moves
    }

}

class MoveDAO (tracer: JRETracer)  {

     val moves = hashMapOf(
        "windmill" to Move(
            "A classic bboy move where the dancer spins around the crown of their head with their legs out",
            "Power Move"
        ),
        "flare" to Move("A classic power move where the dancer throws both legs out in a circle similar to gymnast circles, but with the legs open", "Air Power"),
        "toprock" to Move("The Top Rock is all movement where the breaker is still standing. Set's are typically started with top rock.", "Rocking")
    )

    fun getMoveByName(moveName: String): Move {
        val span = tracer.buildSpan("getMoveByNameDAO").start()
        span.setTag("dao","getmovebyname")
        tracer.scopeManager().activate(span)
        return moves.getValue(moveName)
    }
}
