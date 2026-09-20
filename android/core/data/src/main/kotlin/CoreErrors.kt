package data

import uniffi.mediagram_core.CoreException

/**
 * The sentence a core error carries, or `null` when the failure did not
 * come from the core at all.
 *
 * The generated exception's own `message` renders as `v1=...` — the field
 * name of the bindings' tuple variant — which is not something to put in
 * front of a person. Every one of these strings was written in the core to
 * be read: "That channel has nothing pinned", and what to do about it. The
 * unwrapping happens once, here, so no screen has to know the shape of the
 * generated class.
 *
 * `null` for anything else is the useful half: a keystore that will not
 * open or a file that will not delete is not something the person can fix
 * by choosing differently, and a caller can tell the two apart by whether
 * this answers.
 */
/**
 * What a refresh that did not happen should say, which is always
 * something: [coreSentence] where the core wrote one, the exception's own
 * message where it did not, and a plain statement of what failed where
 * there is neither. Two surfaces report the same refusal — the shelves
 * carry a notice, the System screen carries a row — and a sentence
 * assembled twice is a sentence that can come out two ways.
 */
fun Throwable.refreshSentence(): String =
    coreSentence() ?: message ?: "Could not refresh the library"

fun Throwable.coreSentence(): String? = when (this) {
    is CoreException.Network -> v1
    is CoreException.NotAuthorized -> v1
    is CoreException.NotFound -> v1
    is CoreException.Cipher -> v1
    is CoreException.Io -> v1
    is CoreException.Library -> v1
    else -> null
}
