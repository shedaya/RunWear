// RW-137 S4c unit test for delete-my-data's deleteUserData core, with
// a fake service-role client recording every operation. Asserts:
// per-store sweep coverage (all four storage prefixes incl.
// refs-small, every personal table incl. personal_webhook_events and
// personal_identity_adoptions), step ORDER (tombstone first; RC
// attempt and auth deletion last), and receipt contents.
// Run: deno run --allow-env rw137_delete_my_data_test.ts
import { deleteUserData } from "../functions/delete-my-data/index.ts"

const UID = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
const ops: string[] = []
let failures = 0
function check(name: string, cond: boolean, extra = "") {
  console.log(`${cond ? "PASS" : "FAIL"}  ${name}  ${extra}`)
  if (!cond) failures++
}

// deno-lint-ignore no-explicit-any
function builder(table: string): any {
  // deno-lint-ignore no-explicit-any
  const b: any = {
    _op: "select",
    select: () => { b._op = "select"; return b },
    delete: () => { b._op = "delete"; ops.push(`delete:${table}`); return b },
    upsert: () => { ops.push(`upsert:${table}`); return Promise.resolve({ error: null }) },
    eq: () => b,
    neq: () => b,
    in: () => { b._probe = true; return b },
    or: () => b,
    // deno-lint-ignore no-explicit-any
    then: (resolve: any) => {
      if (b._op === "delete") return resolve({ count: 2, error: null })
      if (b._probe) return resolve({ data: [], error: null }) // no OTHER user shares the hash
      if (table === "personal_reference_photos") {
        return resolve({ data: [{ storage_path: `${UID}/refs/ref-1.jpg` }], error: null })
      }
      if (table === "personal_heroes") {
        // second select (shared-hash probe) resolves empty via neq chain;
        // the fake returns the hero row for the first select as well.
        return resolve({ data: [{ storage_path: `${UID}/heroes/cellA/h.jpg`, image_hash: "hash1" }], error: null })
      }
      return resolve({ data: [], error: null })
    },
  }
  return b
}

const fakeSb = {
  from: (table: string) => builder(table),
  storage: {
    from: (_bucket: string) => ({
      list: (prefix: string) => {
        ops.push(`list:${prefix}`)
        if (prefix === `${UID}/refs-small`) {
          return Promise.resolve({ data: [{ name: "ref-1.jpg", id: "o1" }], error: null })
        }
        if (prefix === `${UID}/tmp`) {
          return Promise.resolve({ data: [{ name: "junk.bin", id: "o2" }], error: null })
        }
        return Promise.resolve({ data: [], error: null })
      },
      remove: (paths: string[]) => {
        ops.push(`remove:${paths.join(",")}`)
        return Promise.resolve({ data: paths.map((name) => ({ name })), error: null })
      },
    }),
  },
}

let rcCalled = false
const receipt = await deleteUserData(
  fakeSb, UID,
  async (id: string) => { ops.push(`auth-delete:${id}`) },
  async (_id: string) => { rcCalled = true; ops.push("rc-delete"); return "skipped_no_secret_key" },
)

check("tombstone written FIRST", ops[0] === "upsert:personal_identity_tombstones", ops[0])
check("storage swept incl. refs-small listing", ops.some(o => o === `list:${UID}/refs-small`))
check("storage swept incl. tmp listing", ops.some(o => o === `list:${UID}/tmp`))
check("storage swept incl. heroes + refs listings",
  ops.some(o => o === `list:${UID}/heroes`) && ops.some(o => o === `list:${UID}/refs`))
const removed = ops.find(o => o.startsWith("remove:")) ?? ""
check("remove covers ref + refs-small derivative + hero + tmp orphan",
  removed.includes("/refs/ref-1.jpg") && removed.includes("/refs-small/ref-1.jpg")
  && removed.includes("/heroes/cellA/h.jpg") && removed.includes("/tmp/junk.bin"), removed)
for (const t of ["personal_generation_jobs", "personal_scaffold_events", "personal_fallback_events",
  "personal_heroes", "personal_cells", "personal_reference_photos", "personal_consents",
  "personal_entitlement_events", "personal_hero_flags", "personal_profiles",
  "personal_webhook_events", "personal_identity_adoptions", "personal_gate_verdicts"]) {
  check(`rows deleted: ${t}`, ops.includes(`delete:${t}`))
}
const authIdx = ops.findIndex(o => o.startsWith("auth-delete:"))
check("auth user deleted LAST", authIdx === ops.length - 1, `idx=${authIdx}/${ops.length}`)
check("RC deletion attempted before auth deletion", rcCalled && ops.indexOf("rc-delete") < authIdx)
check("receipt: tombstone_written", receipt.tombstone_written === true)
check("receipt: rc disposition verbatim", receipt.rc_customer_deletion === "skipped_no_secret_key")
check("receipt: account_deleted", receipt.account_deleted === true)
check("receipt: adoption rows counted", receipt.rows_removed["personal_identity_adoptions"] === 2)

console.log(failures === 0 ? "\nALL UNIT CHECKS PASSED" : `\n${failures} FAILURES`)
Deno.exit(failures ? 1 : 0)
