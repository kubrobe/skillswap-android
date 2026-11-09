package uz.skillswap.app.util

import uz.skillswap.app.data.OfferCard
import uz.skillswap.app.data.remote.Match

/** One card of "Swipe to swap". [theyWant] != null = a two-way swap (they also want something I teach). */
data class DeckCard(val offer: OfferCard, val theyWant: String?, val isMatch: Boolean)

/**
 * Cards for "Swipe to swap": two-way swaps first, then people who teach my wishes,
 * then everyone else (best rated first). Full lessons are left out - you could not book them anyway.
 */
fun buildDeck(offers: List<OfferCard>, matches: List<Match>): List<DeckCard> {
    val byOffer = matches.associateBy { it.offerId }
    return offers
        .filter { it.freeSlots > 0 }
        .map { o -> byOffer[o.offerId].let { m -> DeckCard(o, m?.theyWant, m != null) } }
        .sortedWith(
            compareByDescending<DeckCard> { it.theyWant != null }
                .thenByDescending { it.isMatch }
                .thenByDescending { it.offer.rating }
                .thenByDescending { it.offer.reviewCount }
        )
}

/** Wishes after a swipe session: the old ones plus the skills swiped right, at most 10 (the server limit). */
fun mergeWants(old: List<Long>, liked: List<Long>): List<Long> =
    (old + liked.filter { it > 0 }).distinct().take(10)
