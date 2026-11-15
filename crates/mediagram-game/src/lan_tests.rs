use super::*;

#[test]
fn an_advertised_address_is_taken_as_getprop_prints_it() {
    assert_eq!(parse_advertised("192.168.0.42\n"), Some("192.168.0.42".parse().unwrap()));
}

#[test]
fn an_unset_property_advertises_nothing() {
    assert_eq!(parse_advertised("\n"), None);
    assert_eq!(parse_advertised(""), None);
}

#[test]
fn a_value_that_is_no_address_advertises_nothing() {
    assert_eq!(parse_advertised("funstation.local"), None);
}

#[test]
fn off_android_the_address_is_the_routed_one() {
    assert_eq!(lan_ip(), routed());
}
