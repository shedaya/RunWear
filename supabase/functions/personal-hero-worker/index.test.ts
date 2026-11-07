// RW-120 unit tests: provider failure classification + terminal
// failure text composition. Run with dummy env (the module reads
// its configuration at import). --no-check matches the production
// bundling (transpile-only): the module predates type-checking and
// carries a pre-existing GateFailure shape violation in the draw
// catch's failure literal (noted in the RW-120/121 lane report).
// DENO_CERT points at the VM egress proxy CA, which the sandbox
// requires for the imagescript WASM fetch at import.
//   REPLICATE_API_TOKEN=x SUPABASE_URL=https://placeholder.supabase.co \
//   SUPABASE_SERVICE_ROLE_KEY=x \
//   DENO_CERT=/usr/local/share/ca-certificates/hatch-egress-ca.crt \
//   deno test --no-check --allow-net --allow-env index.test.ts
//
// The Juneau fixtures are VERBATIM from production job
// 45f4ac65 (2026-11-06): the base draws' gate failure and the two
// headband edit predictions' provider errors, exactly as recorded
// in that job's gate_result draws.

import {
  classifyProviderFailure,
  providerErrorCode,
  providerFailureText,
  terminalFailureText,
} from "./index.ts"

function eq(actual: unknown, expected: unknown, label: string) {
  const a = JSON.stringify(actual)
  const e = JSON.stringify(expected)
  if (a !== e) throw new Error(`${label}\n  actual:   ${a}\n  expected: ${e}`)
}
function ok(cond: boolean, label: string) {
  if (!cond) throw new Error(`assertion failed: ${label}`)
}

const JUNEAU_EDIT_ERROR_DRAW_1 =
  "Prediction 1arxzmqty5rmr0d1pgp8wgn9rr failed: The input or output was flagged as sensitive. Please try again with different inputs. (E005)"
const JUNEAU_EDIT_ERROR_DRAW_2 =
  "Prediction zfsfrmb2r9rmw0d1pgpsprss14 failed: The input or output was flagged as sensitive. Please try again with different inputs. (E005)"
const JUNEAU_BASE_GATE_FAILURE = "head seen null != prompt headband"

Deno.test("providerErrorCode extracts the verbatim code", () => {
  eq(providerErrorCode(JUNEAU_EDIT_ERROR_DRAW_1), "E005", "E005 in parens")
  eq(providerErrorCode("some failure with no code"), null, "no code")
  eq(providerErrorCode("upstream said E007 today"), "E007", "bare code")
})

Deno.test("classifyProviderFailure: provider refusals classify", () => {
  eq(classifyProviderFailure(JUNEAU_EDIT_ERROR_DRAW_1)?.code, "E005", "edit prediction E005")
  ok(classifyProviderFailure("Prediction abc123 canceled: ") !== null, "canceled prediction")
  ok(
    classifyProviderFailure('Kontext edit create failed: 422 {"detail":"bad input"}') !== null,
    "edit create refusal",
  )
  ok(classifyProviderFailure("PuLID create failed: 500 boom") !== null, "draw create refusal")
})

Deno.test("classifyProviderFailure: non-refusals stay unclassified", () => {
  eq(classifyProviderFailure("Prediction timed out"), null, "poll timeout is not a refusal")
  eq(classifyProviderFailure("Replicate poll failed: 503"), null, "poll transport error")
  eq(classifyProviderFailure("Output download failed: 500"), null, "download transport error")
  eq(classifyProviderFailure("Hero upload failed: The resource already exists"), null, "storage error")
})

Deno.test("providerFailureText: own class, code preserved, not gate_failed", () => {
  const text = providerFailureText(classifyProviderFailure(JUNEAU_EDIT_ERROR_DRAW_2))
  ok(text.startsWith("provider_error:"), "provider_error prefix")
  ok(!text.startsWith("gate_failed"), "not a gate failure")
  ok(text.includes("E005"), "code preserved")
  ok(text.includes("flagged as sensitive"), "provider message preserved verbatim")
  eq(
    providerFailureText(null),
    "provider_error: provider failure (detail on the draw record)",
    "defensive fallback",
  )
})

// get-personal-hero's lookReasonClass (v15, unchanged by RW-120),
// reproduced verbatim in behaviour: the composed job error string
// is the ONLY input. The Juneau job's pre-fix string classified as
// a quality refusal; the RW-120 string classifies as the service
// refusal it always was.
function lookReasonClass(errorMessage: string): string {
  const msg = (errorMessage ?? "").toLowerCase()
  if (msg.startsWith("gate_failed")) return "quality_refused"
  if (msg.includes("e005") || msg.includes("flagged as sensitive")) {
    return "service_refused"
  }
  return "error"
}

Deno.test("serve-path classification: Juneau before vs after RW-120", () => {
  eq(
    lookReasonClass(`gate_failed: ${JUNEAU_BASE_GATE_FAILURE}`),
    "quality_refused",
    "pre-fix string misclassifies (the RW-120 field case)",
  )
  eq(
    lookReasonClass(providerFailureText(classifyProviderFailure(JUNEAU_EDIT_ERROR_DRAW_1))),
    "service_refused",
    "post-fix string classifies as the service refusal",
  )
  eq(
    lookReasonClass("gate_failed: outer seen true != prompt false"),
    "quality_refused",
    "genuine gate failures still classify as quality refusals",
  )
})

Deno.test("terminalFailureText: Juneau replay composes the provider reason", () => {
  // Replay job 45f4ac65's recorded draws through the RW-120
  // classification: each draw's edit errored at the provider, so
  // each draw's stage is provider_error and the terminal text is
  // composed from the LAST draw's provider failure.
  const pf1 = classifyProviderFailure(JUNEAU_EDIT_ERROR_DRAW_1)
  const pf2 = classifyProviderFailure(JUNEAU_EDIT_ERROR_DRAW_2)
  ok(pf1 !== null && pf2 !== null, "both draws classify as provider failures")
  const text = terminalFailureText({
    lastStage: "provider_error",
    lastFailures: [{ cls: "head", message: JUNEAU_BASE_GATE_FAILURE }],
    identityBest: 0.7235,
    lastProviderFailure: pf2,
    drawsUsedTotal: 2,
    budget: 2,
  })
  eq(text, `provider_error: E005: ${JUNEAU_EDIT_ERROR_DRAW_2}`, "Juneau terminal text")
})

Deno.test("terminalFailureText: pre-existing branches byte-identical", () => {
  eq(
    terminalFailureText({
      lastStage: "prescription_gate",
      lastFailures: [{ cls: "head", message: JUNEAU_BASE_GATE_FAILURE }],
      identityBest: 0.72, lastProviderFailure: null,
      drawsUsedTotal: 2, budget: 2,
    }),
    `gate_failed: ${JUNEAU_BASE_GATE_FAILURE}`,
    "gate branch unchanged",
  )
  eq(
    terminalFailureText({
      lastStage: "identity_screen",
      lastFailures: [], identityBest: 0.31, lastProviderFailure: null,
      drawsUsedTotal: 2, budget: 2,
    }),
    "gate_failed: identity_screen (best similarity 0.31)",
    "identity branch unchanged",
  )
  eq(
    terminalFailureText({
      lastStage: "draw_timeout",
      lastFailures: [], identityBest: null, lastProviderFailure: null,
      drawsUsedTotal: 2, budget: 3,
    }),
    "draw_timeout: draw never returned a usable image within the job's draw budget (2/3 draws used)",
    "generic branch unchanged",
  )
})
