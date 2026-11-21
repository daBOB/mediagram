package catalog

import model.Kind

/**
 * Whether a title page offers the editor's-choice pin. A book is left out: a pinned one would lead the
 * Home page, and the web only ever plays books from their cards, never from a title page with a pin.
 */
fun mayPinEditorsChoice(
    kind: Kind,
    kidsProfile: Boolean,
): Boolean = !kidsProfile && kind != Kind.AUDIOBOOK
