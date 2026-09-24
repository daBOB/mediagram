use super::*;

#[test]
fn io_and_network_causes_never_reach_the_public_error_text() {
    let private = "chat=-100123456 document=987654 token=private-token";
    let io = CoreError::io("opening the catalog")(std::io::Error::other(private));
    let network = CoreError::network("reading the document")(private);
    assert!(matches!(&io, CoreError::Io(message) if message == "opening the catalog"));
    assert!(matches!(&network, CoreError::Network(message) if message == "reading the document"));
    assert_eq!(io.to_string(), "io error: opening the catalog");
    assert_eq!(network.to_string(), "network error: reading the document");
    for error in [io, network] {
        assert!(!format!("{error:?}").contains(private));
    }
}

#[test]
fn logging_a_cause_preserves_each_error_variant_and_its_public_sentence() {
    let refusals = [
        CoreError::NotAuthorized("sign in again".into()),
        CoreError::NotFound("set not found".into()),
        CoreError::Cipher("package failed authentication".into()),
        CoreError::Library("choose a readable library".into()),
    ];
    for refusal in refusals {
        let expected = refusal.to_string();
        let kind = std::mem::discriminant(&refusal);
        let returned = refusal.logged()("private path /home/viewer/session; message=123");
        assert_eq!(std::mem::discriminant(&returned), kind);
        assert_eq!(returned.to_string(), expected);
    }
}

#[derive(Clone, Default)]
struct Logs(std::sync::Arc<std::sync::Mutex<Vec<std::collections::BTreeMap<String, String>>>>);

impl tracing::Subscriber for Logs {
    fn enabled(&self, _: &tracing::Metadata<'_>) -> bool {
        true
    }
    fn new_span(&self, _: &tracing::span::Attributes<'_>) -> tracing::span::Id {
        tracing::span::Id::from_u64(1)
    }
    fn record(&self, _: &tracing::span::Id, _: &tracing::span::Record<'_>) {}
    fn record_follows_from(&self, _: &tracing::span::Id, _: &tracing::span::Id) {}
    fn enter(&self, _: &tracing::span::Id) {}
    fn exit(&self, _: &tracing::span::Id) {}
    fn event(&self, event: &tracing::Event<'_>) {
        struct Fields(std::collections::BTreeMap<String, String>);
        impl tracing::field::Visit for Fields {
            fn record_debug(&mut self, field: &tracing::field::Field, value: &dyn std::fmt::Debug) {
                self.0.insert(field.name().into(), format!("{value:?}"));
            }
        }
        let mut fields = Fields(Default::default());
        event.record(&mut fields);
        self.0.lock().unwrap().push(fields.0);
    }
}

fn assert_full_cause(make: impl FnOnce(anyhow::Error) -> CoreError, public: &str) {
    let logs = Logs::default();
    let cause = anyhow::anyhow!("disk full; document=987654")
        .context("reading private catalog path")
        .context("refreshing the local library");
    let error = tracing::subscriber::with_default(logs.clone(), || make(cause));
    assert_eq!(error.to_string(), public);
    assert!(!format!("{error:?}").contains("987654"));
    let captured = logs.0.lock().unwrap();
    assert_eq!(captured.len(), 1);
    assert_eq!(
        captured[0].get("cause").unwrap(),
        "refreshing the local library: reading private catalog path: disk full; document=987654"
    );
}

#[test]
fn io_diagnostics_keep_nested_context_and_the_underlying_cause() {
    assert_full_cause(
        CoreError::io("opening the catalog"),
        "io error: opening the catalog",
    );
}

#[test]
fn network_diagnostics_keep_nested_context_and_the_underlying_cause() {
    assert_full_cause(
        CoreError::network("reading the document"),
        "network error: reading the document",
    );
}

#[test]
fn fixed_variant_diagnostics_keep_nested_context_and_the_underlying_cause() {
    assert_full_cause(
        CoreError::Library("choose a readable library".into()).logged(),
        "library error: choose a readable library",
    );
}
