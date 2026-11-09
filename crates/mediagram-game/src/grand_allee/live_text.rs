//! How the live game writes a sum of money.

/// 12000 as "12.000".
pub(crate) fn grouped(n: i32) -> String {
    let digits = n.abs().to_string();
    let groups: Vec<String> = digits.as_bytes().rchunks(3).rev().map(|c| String::from_utf8_lossy(c).into_owned()).collect();
    format!("{}{}", if n < 0 { "-" } else { "" }, groups.join("."))
}

pub(crate) fn taler(n: i32) -> String {
    format!("{} Taler", grouped(n))
}

#[cfg(test)]
mod tests {
    use super::{grouped, taler};

    #[test]
    fn money_is_grouped_in_thousands_with_dots() {
        assert_eq!(grouped(0), "0");
        assert_eq!(grouped(999), "999");
        assert_eq!(grouped(12000), "12.000");
        assert_eq!(grouped(-1234567), "-1.234.567");
        assert_eq!(taler(2500), "2.500 Taler");
    }
}
