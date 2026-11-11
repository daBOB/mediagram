package com.mediagram.spieltisch

import androidx.annotation.DrawableRes
import uniffi.spieltisch_core.GameEntry

/** A game's poster, which carries its own title, and the backdrop behind the shelf while it has focus. */
data class GameArt(
    @DrawableRes val poster: Int,
    @DrawableRes val backdrop: Int,
)

/** One game on the shelf: [kind] is the host's name for it, which the TV page takes as `?game=`. */
data class ShelfGame(
    val kind: String,
    val name: String,
    val players: String,
    val art: GameArt?,
)

/** The art there is, by the host's game kind. */
private val ArtByKind =
    mapOf(
        "quiz" to GameArt(R.drawable.game_poster_quiz, R.drawable.game_backdrop_quiz),
        "poster" to GameArt(R.drawable.game_poster_poster, R.drawable.game_backdrop_poster),
        "mau_mau" to GameArt(R.drawable.game_poster_mau_mau, R.drawable.game_backdrop_mau_mau),
        "schwimmen" to GameArt(R.drawable.game_poster_schwimmen, R.drawable.game_backdrop_schwimmen),
        "durak" to GameArt(R.drawable.game_poster_durak, R.drawable.game_backdrop_durak),
    )

/** The host's catalogue as the shelf shows it, in the lobby's order; a game without art yet shows its name. */
fun shelfGames(catalogue: List<GameEntry>): List<ShelfGame> =
    catalogue.map { ShelfGame(it.kind, it.name, "${it.minPlayers}–${it.maxPlayers} players", ArtByKind[it.kind]) }
