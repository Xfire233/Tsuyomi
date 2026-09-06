<!-- SPDX-FileCopyrightText: 2026 Tsuyomi Contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Development tooling registry

This file is the canonical inventory and dispatch policy for agent-facing development resources. It answers three questions: which resource owns a task, when it is mandatory, and when it must not be invoked. Product behavior, current Phase authorization, generated evidence, machine state, credentials, and provider-private configuration belong to their own authorities and are never copied here.

## Resource record standard

Every registered resource is described with the same fields:

| Field | Meaning |
|---|---|
| Owner | Canonical implementation/configuration and the client responsible for it. |
| Trigger | Observable condition that makes the resource mandatory. |
| Preconditions | Required skill read, authority read, input, or environment state. |
| Method | The bounded operation the resource performs. |
| Output | Fact, edit, evidence, or artifact it is allowed to own. |
| Do not use | Overlap, authority, privacy, or destructive cases that exclude it. |
| Fallback | Next owner, allowed only after the primary owner is unavailable or has failed. |
| Health check | Non-destructive proof that discovery/transport works. |
| Scope | Inputs, surfaces and side effects the resource may touch. |
| Completion | Observable condition that releases the resource; includes required cleanup or handoff. |

Missing fields are governance defects. Availability is runtime state: a registered resource being disconnected does not transfer ownership to a duplicate tool. A tool call ends when its Completion condition is met; it must not continue exploring, retrying or collecting evidence without a new trigger.

## Canonical locations

| Resource | User scope | Tsuyomi project scope | Rule |
|---|---|---|---|
| Session instructions | `~/.omp/agent/AGENTS.md` and `RULES.md` | `AGENTS.md`, component `CONTRIBUTING.md`, authoritative contracts | Durable project rules stay versioned; personal configuration never becomes product authority. |
| Skills | `~/.agents/skills/<name>/SKILL.md` | `.agents/skills/<name>/SKILL.md` | One canonical directory. Provider-specific paths may be generated links, never independent copies. |
| MCP | `~/.omp/agent/mcp.json` | `.omp/mcp.json` only for a project-owned server | OMP owns shared definitions and allow/deny policy; provider-internal runtime servers remain with their provider. |
| Local continuity/evidence | — | `.local/ACTIVE_HANDOFF.md`, `.local/ai-reviews/`, build outputs | Ignored, transient, non-authoritative, and never a substitute for contracts or tests. |

`~/.agents/skills` is the cross-agent user source. Link it to supported clients with the Skills CLI; do not copy skill directories between Claude Code, Codex, OpenCode, GitHub Copilot, and OMP.

## Deterministic dispatch

1. Classify the required output: repository fact, symbol fact, current web fact, library API fact, code edit, Android build result, device fact, visual evidence, maintainability report, or issue handoff.
2. Read every matching Skill before invoking its workflow. Read the owning contract before a tool can change product-visible or security-sensitive behavior.
3. Use the first owner in the tables below. A familiar fallback is not an alternative first choice.
4. Escalate only when the first owner is unavailable or fails for the required capability. Record the failed capability; do not rerun equivalent tools to manufacture duplicate evidence.
5. One observable claim has one evidence owner. Behavior, geometry, pixels, device state, and human judgment are different claims and may therefore have different owners.
6. Stop before destructive, account-affecting, remote-write, canonical-device, publication, merge, or release actions unless the current authorization explicitly covers them.

## Scope and completion

| Resource | Scope | Completion / stop condition |
|---|---|---|
| `read` | One known path/URL and only the sections needed for the current decision | Required source facts are grounded; stop before unrelated sections or linked documents whose triggers do not match |
| `glob` | Path structure under explicit roots/patterns | Candidate paths are identified; stop and hand the selected path to `read`/`grep` |
| `grep` | Text/regex matches under explicit roots | Relevant definitions/callsites/conflicts are located or absence is established with an appropriately broadened query |
| LSP | Semantic symbols supported by the configured language server | Definitions/references/diagnostics/refactor results are complete; after an applied refactor, affected diagnostics are clean or recorded |
| `ask` | One user decision with materially different tradeoffs that repository/context/tool research cannot resolve | A concrete option or typed answer is returned; resume execution and do not ask again unless a new decision appears |
| `todo` | One explicit checklist or non-trivial task with at least three distinct steps | Every item is completed, dropped, or externally blocked; clear/replace stale lists rather than keeping parallel task state |
| `multi_tool_use.parallel` | Independent tool calls with no ordering, shared-state, or edit dependency | Every call returns or fails independently; dependent follow-up begins only after the barrier |
| `task` | User/Skill-authorized parallel subagent slices with contracts and non-overlapping ownership | Every child settles, claimed work is verified, and unused children are cancelled/released |
| `eval` | One incremental persistent-kernel computation or browser-control step not better owned by a specialized tool | Required computed/interactive result exists; close browser tabs and release external resources while retaining only useful kernel state |
| `edit` | Displayed, anchored lines of existing files | Intended surgical changes apply with no rejected/stale hunk; re-ground before another edit |
| `write` | New file, intentional complete replacement, archive/database write, or mounted `xd://` invocation | Complete content/request is accepted and its format/consumer validates; do not use it for follow-up surgical edits |
| `ast_edit` | Structural codemod across explicitly named parseable source paths | Staged matches are reviewed and explicitly resolved or rejected; affected parser/compiler checks then own verification |
| `bash` | One bounded external binary or short fact pipeline | Command exits and its exact result is captured; no service/process remains unmanaged |
| `hub` | One named long-running process, subagent message, or background-job lifecycle | Required reply/state/output is observed; stop/release the process or child unless persistence was explicitly required |
| `debug` | One launched/attached runtime debug session and explicitly named state/breakpoints | Required runtime state is observed or changed, then the debug session is terminated |
| Mnemopi memory | Durable project/user decisions or cross-session history relevant to the current task | Recalled facts are reconciled with current authority; only durable normalized facts are retained; stale memory is updated/invalidated |
| `web_search` | Current external facts not available at a known URL or library-doc owner | Claims are corroborated and cited; stop before general browsing unrelated to the question |
| Browser | One interactive/authenticated JavaScript surface | Requested interaction/state is observed and the managed tab is closed/released |
| `image_gen` | One requested generated image or edit to a supplied image | Requested image is returned once; it is not reused as runtime screenshot or product-approval evidence |
| `xd://report_issue` | One reproducible mismatch between a tool's documented behavior and observed output | A concise tool/behavior report is accepted; continue with a safe fallback or record the blocker |
| Gradle Wrapper | Affected Android compile/lint/test/build targets | Selected tasks exit and outputs match the claimed verification; do not expand to unrelated full-suite work |
| Repository policy / REUSE | Versioned repository artifacts, registry drift and licensing | Both selected checks pass or exact violations are reported; generated bytecode/build residue is removed |
| `android-cli` | Official Android docs plus authorized SDK/AVD/device/app operations | Requested fact/deployment/layout/PNG/Journey is produced and device ownership/state is recorded; temporary capture/setup is cleaned |
| `tsuyomi-android-review` | Changed Android UI/review surfaces selected by policy | UI-R-selected evidence owners finish, AI state remains `PENDING`, human-only items and device ownership are handed off |
| `frontend-design` | Candidate visual direction for a new/materially reshaped visible surface | One direction is selected and critiqued against Tsuyomi/Android authority; implementation/review passes to project owners |
| `screenshot` | Explicit desktop/system capture or last-resort surface without native capture | Requested image is saved/returned once; temporary capture files are removed unless evidence ownership requires retention |
| `smell-check` | User-selected source/test path set, read-only | Report covers the requested profile/path set with evidence strengths; no fixes are applied |
| `find-skills` | Discovery/evaluation of an installable capability | Candidates are assessed for owner overlap, provenance, license, maintenance and security; installation requires a separate justified action |
| `to-spec` | Decided future work matching repository trigger policy | Owning issue is created/amended, or pending spec plus publication blocker is recorded; it never leaves binding decisions only in the issue |
| Context7 | One identified third-party library and API/version question | Library ID and relevant current documentation answer are captured; stop before general web/platform research |
| UIAutomator2 | One Android CLI capability gap for input/system UI/hierarchy | Requested interaction or single fallback hierarchy completes; disconnect/release and do not duplicate working Android CLI evidence |
| `node_repl` | No OMP scope | Never starts in OMP |
| `websearch` | No OMP scope | Never starts in OMP; use native `web_search` |
| `grep_app` | No OMP scope | Never starts in OMP; use native `grep` |

## Native and repository tools

| Resource | Owner | Trigger | Preconditions | Method | Output | Do not use | Fallback | Health check |
|---|---|---|---|---|---|---|---|---|
| `read` | OMP native | Known file, URL, document, archive, database, image or video | Path/URL is known; select only needed ranges | Read bounded selector or decoded resource | Source fact or decoded content | Browser for static content; shell paging/listing | Browser only for JavaScript, authenticated or interactive state | Read a harmless known repository file |
| `glob` | OMP native | Repository path structure or candidate discovery | Explicit root/pattern | Match paths | Candidate paths | Shell `find` or directory enumeration | Narrow the pattern; no alternate owner | Match one known project pattern |
| `grep` | OMP native | Text/regex location, conflict or absence | Explicit roots and appropriately scoped pattern | Search with built-in regex engine | Anchored matches | Shell `grep`, `rg` or `awk`; semantic reference work when LSP exists | Narrow/broaden roots/pattern; never shell search | Search a known literal in one file |
| LSP | OMP `xd://lsp` | Definition, reference, hover, type, implementation, rename, import fix or diagnostics | Read LSP device docs; supporting server available; exported-symbol edits require references first | Invoke semantic operation; apply server refactor/action where appropriate | Semantic result or applied refactor | Text/manual cross-file rename while supported | Targeted compiler/search evidence only when no server supports the file type | Query capabilities/status for the affected language |
| `ask` | OMP native | User must choose among materially different tradeoffs | Exhaust repository/context/tool evidence; provide two to five distinct options | Ask one grouped decision | User-selected option or typed answer | Asking for facts available in files/tools; choices with a safe standard default | Choose the conservative standard when tradeoff is not material | Not applicable without a real decision trigger |
| `todo` | OMP native | User checklist or non-trivial work with at least three steps | Exact 5–10 word task identities are known | Initialize/update one phased list alongside real work | Current execution state | Trivial work, a todo-only turn, duplicate task state | Plain execution for fewer than three steps | View the current list when exact task text is lost |
| `multi_tool_use.parallel` | OMP native | Two or more tool calls are independent | No output/order/shared edit dependency | Dispatch one parallel batch | Independent results | Dependent calls or concurrent edits to shared state | Sequential calls when dependency exists | Run only with harmless independent reads/checks |
| `task` | OMP native | User explicitly requests agents/parallelization or an applicable Skill/AGENTS rule requires delegation | Decompose independent slices; define shared contracts; select specific agent types; skip per-child validation | Dispatch one concurrent batch and coordinate by hub | Child result/artifact/edit | Generic top-level planning, one slice, overlapping file ownership, unauthorized delegation | Main agent executes directly | Spawn only under a valid delegation trigger |
| `eval` | OMP native | Persistent Python/JS state, one-step computation, custom browser control or temporary tool definition | No simpler specialized tool owns the operation; browser tab opened before use | Run one incremental cell and reuse kernel state | Computed fact, structured result or interactive evidence | Routine file/search/edit/shell replacement; unclosed tabs or services | Specialized owner for the operation | Evaluate a side-effect-free expression |
| `edit` | OMP native | Surgical existing-file change | Latest anchored lines read; changed ranges alone selected | Apply line-anchored patch | Updated existing file | Whole-file rewrite for a small change; stale/unseen ranges | `write` only for genuine full replacement | Apply only after an anchored read |
| `write` | OMP native | New file, full replacement, archive/database operation or mounted device invocation | Complete content/schema known; mounted-device docs read before first use | Write full content or JSON request | File, row or device result | Incremental existing-file edits; unrequested Markdown creation | `edit` for surgical changes | Write only to a safe temporary target or required device route |
| `ast_edit` | OMP `xd://ast_edit` | Multi-file structural codemod where text replacement is unsafe | Read device docs; parseable languages; explicit paths and AST patterns | Stage rewrite, inspect proposal, then resolve/reject | Applied or discarded structural rewrite | One-off text edit, cross-file symbol rename supported by LSP, malformed parse | LSP rename/refactor or anchored `edit` | Stage and reject a bounded known match |
| `bash` | OMP native | One external CLI or short fact pipeline | Specialized tool does not own the operation; bounded command/cwd | Execute one process/pipeline | Captured exit/output | File reads/search/edits, services, debuggers, complex scripts | Matching specialized tool | Run a harmless version/count command |
| `hub` | OMP native | Long-running service/process, subagent coordination or background-job lifecycle | Stable process/peer/job identity; readiness/stop condition defined | Start/message/wait/log/stop named owner | Reply, readiness, logs or lifecycle state | Unmanaged background shell process; polling when auto-delivery suffices | No unmanaged fallback | List peers/processes or start a harmless bounded service only when needed |
| `debug` | OMP `xd://debug` | Breakpoints, stepping, threads, variables or runtime memory are required | Read device docs; target/adapter/session known; no active conflicting session | Launch/attach and perform bounded DAP operations | Runtime state or controlled mutation | Shell debugger, logging-only substitution, source-only reasoning when runtime state is required | Targeted reproduction/instrumentation if adapter unavailable | List sessions or launch only under a real debug trigger |
| Mnemopi memory | OMP `recall`/`reflect`/`retain`/`memory_edit` | Prior decisions/preferences/history affect current work, or a durable normalized decision was reconciled | Current repository/user authority checked first; full memory read before update | Recall/reflect, then retain/update/invalidate only durable facts | Relevant memory context or updated durable record | Secrets, raw prompts/transcripts, ephemeral task state, authorization grants | Repository handoff for transient state | Recall one non-sensitive project topic |
| `web_search` | OMP native | Current external information without a known URL/library owner | Search question and recency known; prefer primary sources | Search and corroborate | Cited current sources | Known URL, library API docs, repository content | Direct `read` for known URLs; Context7 for library APIs | Run only for a real current-information question |
| Browser | OMP browser via `eval` | Interactive/authenticated JavaScript web UI | Open dedicated/authorized tab; static `read` insufficient | Observe/interact/evaluate, visually confirm, close tab | Actual page state or interaction evidence | Static pages; navigating user's visible relay tab without authorization | `read` for static content | Open and close a harmless public page |
| `image_gen` | OMP image tool | User requests image generation or editing | Concrete prompt/image; product authority already known when applicable | Generate/edit one image | Image artifact | Runtime screenshot, UI verification, evidence fabrication | Existing assets/manual design workflow | Not invoked without a real generation request |
| `xd://report_issue` | OMP mounted QA reporter | Tool output contradicts documented behavior for the supplied parameters | Preserve concise reproducible mismatch without private data | Write one plain tool/behavior report | Accepted QA report | Product bug reports, speculative complaints, retries without evidence | Continue with safe owner/fallback | Not invoked without an observed tool mismatch |
| Gradle Wrapper | Repository-owned wrapper | Android build, compiler, lint, unit/instrumentation or screenshot test | Affected tasks/variant/device authorization known | Run selected wrapper tasks | Build/test result | Android Studio as a duplicate build path; unrelated full suite | CI only when required environment unavailable locally | Run a bounded help/task query when needed |
| Repository policy / REUSE | `tools/check_repository.py`, unit tests and REUSE CLI | Repository artifact, registry-drift or licensing verification | Repository root and affected scope known; suppress/remove generated bytecode | Run exact policy/tests/REUSE checks | Policy/license result | Manual inventory as final proof | None | Run repository checker and relevant unit tests |

## Skills

Read a matching skill before use. OMP registers skills at session startup; after installing, moving, or enabling one, restart OMP before testing `skill://<name>`.

| Skill | Owner | Trigger | Preconditions | Method | Output | Do not use | Fallback | Health check |
|---|---|---|---|---|---|---|---|---|
| `android-cli` | User: `~/.agents/skills/android-cli` | Android platform docs, SDK/AVD lifecycle, deployment, device inspection, hierarchy, PNG or Journey work | Read the Skill; identify device/package ownership and authorization before state change | Use the documented `android` command workflow for the bounded docs/build/device/capture operation | Official-doc fact, AVD/device fact, deployment, layout, PNG or Journey evidence | Gradle correctness, desktop screenshots or bypassing device authorization | UIAutomator2 only for input/system UI or one failed hierarchy capability | `android doctor` or read-only device listing |
| `tsuyomi-android-review` | Project: `.agents/skills/tsuyomi-android-review` | Android UI, navigation, interaction, accessibility, display-profile, screenshot or Review Graph change | Read this Skill, `android-cli`, active policy, owning UI authority, production evidence obligation, catalog node, implementation and tests | Execute only policy-selected `UI-R0–UI-R4.1` passes and evidence lanes | UI-R impact selection, bounded production-device evidence, changed-transition Journeys and human-review handoff | Non-UI work, product authority, AI approval, fixture-only finalization or automatic canonical replacement | Human owns qualitative verdict; no duplicate UI reviewer owner | Skill Python tests plus workflow-only UI-R1 change detection |
| `frontend-design` | User: `~/.agents/skills/frontend-design` | Creating or materially reshaping a visible UI | Read the Skill and owning Tsuyomi UI authority first | Produce and critique one intentional visual direction before implementation | Candidate visual direction and critique | Product behavior, Material/API correctness, minor behavior-only fixes or final approval | Existing design system plus official Android guidance | `read skill://frontend-design` |
| `screenshot` | User: `~/.agents/skills/screenshot` | User explicitly requests desktop/system capture, or no surface-specific capture exists | Read the Skill; establish that Android/browser/native capture does not own the surface | Run one OS-level capture workflow | Desktop/system screenshot | Android capture when Android CLI works; browser capture when browser owns the page | Surface-specific capture first | `read skill://screenshot` |
| `smell-check` | User: `~/.agents/skills/smell-check` | User explicitly requests smell, maintainability, tech-debt, duplication, nesting or test-smell audit | Read the Skill; user supplies or accepts a path scope/profile | Run the prescribed read-only measurement and evidence-strength audit | Scoped smell report | PR/security review, implementation, formatting or generic quality check | Normal review for correctness/security | `read skill://smell-check` |
| `find-skills` | User: `~/.agents/skills/find-skills` | User asks whether an installable Skill can perform a capability or asks to discover one | Read the Skill; identify existing owner and evaluation criteria | Discover candidates and assess overlap, provenance, license, maintenance and security | Candidate inventory and adoption recommendation | Silent installation, search-rank authority or replacing an owner without conflict analysis | Manual upstream research | `read skill://find-skills` |
| `to-spec` | User: `~/.agents/skills/to-spec` | Repository trigger policy in `DESIGN_MEMORY_WORKFLOW.md` matches | Read the Skill; reconcile repository authority; verify GitHub auth and exact label before publication | Package the decided work into the owning issue or ignored pending spec | `ready-for-agent` issue/amendment or pending spec with blocker | Small isolated correction, unresolved interview, substitute for implementation or duplicate owning issue | Amend existing owning issue when appropriate | Skill discovery plus GitHub authentication and label lookup |

## MCP ownership

The user OMP configuration owns shared MCP definitions. Project documentation records intended dispatch, not private endpoints or credentials.

| Server | Owner/state | Trigger | Preconditions | Method | Output | Do not use | Fallback | Health check |
|---|---|---|---|---|---|---|---|---|
| `context7` | OMP-owned, enabled | Current third-party library API, version behavior, migration or code examples when repository/known official docs do not answer | Library/product identity known; repository and known URL owners exhausted | Resolve the library ID, then query only the required current docs | Library documentation fact with library identity | Android platform docs, known URLs, general web research or repository search | Official known URL via `read`, otherwise `web_search` | Resolve one public library ID, then query one harmless topic |
| `uiautomator2` | OMP-owned, enabled | Android CLI cannot perform required input/system-UI interaction, or its hierarchy capability failed once while device remains healthy | Read mounted tool docs; establish Android CLI limitation; initialize the selected device; preserve authorization/data boundaries | Invoke only the missing operation, then disconnect/release | Input result, system-UI result or one fallback hierarchy | Duplicate Android CLI PNG/layout evidence, routine app control, unauthorized clear/install or private UI capture | Bounded `adb` only when both higher-level owners lack the capability | Read-only device list |
| `node_repl` | Provider-internal, disabled in OMP | Never in OMP | None | Do not invoke | None | Importing provider-native browser-pipe state | Owning provider only | Confirm absent/disabled only during tooling maintenance |
| `websearch` | Provider duplicate, disabled in OMP | Never in OMP | None | Do not invoke | None | Duplicating OMP `web_search` | OMP `web_search` | Confirm absent/disabled only during tooling maintenance |
| `grep_app` | Provider duplicate, disabled in OMP | Never in OMP | None | Do not invoke | None | Duplicating OMP `grep` | OMP `grep` | Confirm absent/disabled only during tooling maintenance |

Never copy a provider configuration wholesale. It may mix model credentials, environment values, private endpoints, and native-pipe state with MCP definitions. Shared secrets use environment-variable indirection or OMP-managed authentication storage.

## Ambiguity and exclusion examples

| Request | Correct dispatch | Explicit exclusion |
|---|---|---|
| “Where is this symbol used?” | LSP references | Regex search unless no language server supports the file type |
| “What does this known documentation URL say?” | `read` URL | `web_search` and browser |
| “What is the current kotlinx.coroutines API?” | Context7 | General web search as first choice |
| “Check Android's official edge-to-edge guidance” | `android-cli` docs | Context7 |
| “Capture this Android screen” | Android CLI | Desktop `screenshot` Skill and duplicate UIAutomator2 capture |
| “Tap a permission dialog Android CLI cannot reach” | UIAutomator2 after Android CLI limitation is established | Reinstalling/clearing the app |
| “Review this PR for correctness” | Adviser/reviewer | `smell-check` |
| “Scan this directory for maintainability smells” | `smell-check` | Generic PR review |
| “Record this decided multi-module feature for later” | `to-spec` | A new issue for a one-line correction or unresolved design |
| “Implement this current feature now” | Owning implementation workflow | `to-spec` as a substitute for implementation |
| “Which of these materially different storage contracts should we adopt?” | `ask` after repository evidence is exhausted | Asking for a file/tool-provided fact or avoiding a safe conventional default |
| “Parallelize these three independent implementation slices” | `task` with one shared contract and non-overlapping ownership | A plain parallel tool batch or generic planning subagent |
| “Run these independent read-only checks together” | `multi_tool_use.parallel` | Subagents or parallel calls with order/shared-state dependencies |
| “Rename this exported symbol across the project” | LSP rename | `ast_edit` or manual text replacement |
| “Apply this repeated syntax transformation across these files” | `ast_edit`, then explicitly resolve/reject | One-off anchored edit or LSP-supported symbol rename |
| “Inspect the value at the crash breakpoint” | `debug` | Adding logs or using shell debugger commands as a substitute |
| “Remember this reconciled design decision next session” | Mnemopi `retain` after repository authority is updated | Raw prompts, transient task state or authorization grants |
| “Generate or edit this illustration” | `image_gen` | Desktop/Android/browser screenshot capture or runtime evidence |

## Discovery and health checks

Run read-only checks without printing credentials or raw provider configuration:

```text
npx skills list -g
claude plugin list
claude mcp list
codex mcp list
opencode mcp list
omp config get skills.enableAgentsUser
omp config get skills.enableAgentsProject
omp config get skills.enableClaudeUser
omp config get skills.enableCodexUser
```

After restarting OMP, read each required `skill://` entry and run only the MCP's non-destructive health check. A missing Skill is a startup/discovery defect first. A disconnected MCP is diagnosed at its owning configuration and transport before any duplicate server is added.

## Change procedure

1. Classify the resource as instruction, Skill, MCP, native tool, provider-internal server, or local evidence.
2. Identify its single owner and overlap with every existing registered resource.
3. Read the complete Skill/server definition and review provenance, maintenance, license, permissions, secrets, and destructive capabilities.
4. Define Trigger, Preconditions, Method, Output, Scope, Completion, Do not use, Fallback, and Health check before installation or enablement.
5. Put user Skills in `~/.agents/skills`; put project Skills in `.agents/skills`; generate provider links instead of copies.
6. Put shared MCP definitions/deny policy in `~/.omp/agent/mcp.json`; keep provider-native runtime servers with their provider.
7. Update this registry, affected instructions, and executable governance tests in the same change.
8. Restart the owning client, validate discovery with non-destructive checks, then remove obsolete copies/settings.

CI validates versioned project Skills and documentation drift only. It must never inspect a contributor's home directory or require user-scoped Skills/MCP servers.
