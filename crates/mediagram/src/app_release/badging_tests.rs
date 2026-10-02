use super::*;

const LINE: &str = "package: name='com.mediagram.android' versionCode='93000' versionName='0.93.0' platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'\nsdkVersion:'24'\n";

#[test]
fn the_package_line_is_read() {
    let badging = parse(LINE).unwrap();
    assert_eq!(badging.package, "com.mediagram.android");
    assert_eq!(badging.version_code, 93_000);
    assert_eq!(badging.version_name, "0.93.0");
}

#[test]
fn output_without_a_usable_package_line_is_an_error() {
    assert!(parse("sdkVersion:'24'\n").is_err());
    assert!(parse("package: name='x' versionCode='abc' versionName='1'\n").is_err());
    assert!(parse("package: name='x' versionName='1'\n").is_err());
}
