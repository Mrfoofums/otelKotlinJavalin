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

    app.error(404) { ctx->
        ctx.result("YOU DONE GOOFED, 404 MAN!!!")
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
