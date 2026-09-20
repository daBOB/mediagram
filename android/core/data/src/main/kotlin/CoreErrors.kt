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
fun Throwable.coreSentence(): String? = when (this) {
    is CoreException.Network -> v1
    is CoreException.NotAuthorized -> v1
    is CoreException.NotFound -> v1
    is CoreException.Cipher -> v1
    is CoreException.Io -> v1
    is CoreException.Library -> v1
    else -> null
}
