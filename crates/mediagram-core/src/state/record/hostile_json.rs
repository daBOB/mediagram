//! Reading a `serde_json::Value` as if it came from a stranger: the
//! coercions `record.rs` and `list_record.rs` both lean on, so a missing or
//! wrong-typed key degrades to "nothing said" rather than a parse failure
//! that would take the rest of the document down with it.

use serde_json::Value;

pub(super) fn as_array(value: Option<&Value>) -> &[Value] {
    match value {
        Some(Value::Array(items)) => items,
        _ => &[],
    }
}

/// A non-empty string, trimmed — the only kind of text worth keeping here.
pub(super) fn text_(value: Option<&Value>) -> Option<String> {
    let clean = value?.as_str()?.trim();
    (!clean.is_empty()).then(|| clean.to_string())
}

/// `Number(value)`, the coercion `parseRecord` leans on throughout: a
/// missing key is `undefined` and becomes NaN, `null` becomes 0, booleans
/// become 0/1, a numeric string is parsed and anything else is NaN.
pub(super) fn js_number(value: Option<&Value>) -> f64 {
    let Some(value) = value else { return f64::NAN };
    match value {
        Value::Null => 0.0,
        Value::Bool(b) => {
            if *b {
                1.0
            } else {
                0.0
            }
        }
        Value::Number(n) => n.as_f64().unwrap_or(f64::NAN),
        Value::String(s) => {
            let trimmed = s.trim();
            if trimmed.is_empty() {
                0.0
            } else {
                trimmed.parse::<f64>().unwrap_or(f64::NAN)
            }
        }
        Value::Array(_) | Value::Object(_) => f64::NAN,
    }
}

pub(super) fn is_integer(n: f64) -> bool {
    n.is_finite() && n.fract() == 0.0
}
