package catalog

import model.MediaSet

/**
 * Every set anywhere in [shelves], keyed by id — [indexById] made public for
 * callers outside this module. The phone's Series and Tutorials department
 * pages need it for the same reason [homeRowsOf] already does: a Continue
 * row resolves a progress row to its set before this department's own
 * [showsDepartmentOf] narrows it to one kind, and a progress row can name a
 * set of any kind.
 */
fun allSetsById(shelves: List<Shelf>): Map<String, MediaSet> = indexById(shelves)
