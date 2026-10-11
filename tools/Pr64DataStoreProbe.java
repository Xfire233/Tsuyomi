// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import java.io.IOException;
import java.util.*;

/** Removable PR64 observer. The runner owns PID binding, deadlines and process cleanup. */
public final class Pr64DataStoreProbe {
    private static final String DS = "androidx.datastore.core.DataStoreImpl";
    private static final String ACTOR = "androidx.datastore.core.SimpleActor";
    private static final String UPDATE = "androidx.datastore.core.Message$Update";
    private static final String PREF = "androidx.datastore.preferences.core.PreferenceDataStore";
    private static final String PREF_TRANSFORM = PREF + "$updateData$2";
    private static final String EDIT = "androidx.datastore.preferences.core.PreferencesKt$edit$2";
    private static final String JOB = "kotlinx.coroutines.JobSupport";
    private static final String ABSTRACT = "kotlinx.coroutines.AbstractCoroutine";
    private static final String SCOPE = "kotlinx.coroutines.internal.ScopeCoroutine";
    private static final String AWAIT = JOB + "$AwaitContinuation";
    private static final String CCI = "kotlinx.coroutines.CancellableContinuationImpl";
    private static final String DC = "kotlinx.coroutines.internal.DispatchedContinuation";
    private static final String BASE = "kotlin.coroutines.jvm.internal.BaseContinuationImpl";
    private static final String LINK = "kotlinx.coroutines.internal.LockFreeLinkedListNode";
    private static final String CHILD = "kotlinx.coroutines.ChildHandleNode";
    private static final String WAITER = "kotlinx.coroutines.ChildContinuation";
    private static final String BLOCKING = "kotlinx.coroutines.BlockingCoroutine";
    private static final String TEST = "org.tsuyomi.android.LocalPublicationProductionJourneyInstrumentedTest";
    private static final String APP = "org.tsuyomi.android.TsuyomiApplication";
    private static final String UI = "androidx.compose.ui.test.";
    private static final String APPLY = UI + "ApplyingContinuationInterceptor";
    private static final String FRAME = UI + "FrameDeferringContinuationInterceptor";
    private static final String STANDARD = "kotlinx.coroutines.test.StandardTestDispatcherImpl";
    private static final String SCHEDULER = "kotlinx.coroutines.test.TestCoroutineScheduler";
    private static final String EVENT = "kotlinx.coroutines.test.TestDispatchEvent";
    private static final String HEAP = "kotlinx.coroutines.internal.ThreadSafeHeap";
    private static final String TEST_DISPATCHER = "kotlinx.coroutines.test.TestDispatcher";
    private static final String PROCESS_EVENT = "processEvent$kotlinx_coroutines_test";
    private static final Map<String, String> PUMP_METHODS = Map.of(
            "tryRunNextTaskUnless$kotlinx_coroutines_test", "(Lkotlin/jvm/functions/Function0;)Z",
            "advanceUntilIdle", "()V",
            "advanceUntilIdleOr$kotlinx_coroutines_test", "(Lkotlin/jvm/functions/Function0;)V",
            "runCurrent", "()V",
            "advanceTimeBy", "(J)V",
            "advanceTimeBy-LRDsOJo", "(J)V");
    private static final int LIMIT = 32;
    private static final int GRAPH_LIMIT = 128;
    private final VirtualMachine vm;
    private final Map<String, ReferenceType> types = new HashMap<>();
    private final Map<String, Field> fields = new HashMap<>();
    private final Map<String, Object> captured = new LinkedHashMap<>();
    private boolean closed;
    private volatile boolean suspended;
    private long captureDeadline;
    private int reads;
    private final List<String> cleanupErrors = new ArrayList<>();
    private final List<PumpFrame> pumpFrames = new ArrayList<>();
    private final List<Map<String, Object>> pumpGaps = new ArrayList<>();

    private Pr64DataStoreProbe(VirtualMachine vm) { this.vm = vm; }

    private static final class Gap extends RuntimeException {
        final String code;
        final Map<String, Object> details;
        Gap(String code) { this(code, Map.of()); }
        Gap(String code, Map<String, Object> details) {
            this.code = code;
            this.details = details;
        }
    }

    public static void main(String[] args) {
        Pr64DataStoreProbe probe = null;
        Thread shutdown = null;
        Map<String, Object> report = null;
        String phase = "preflight";
        int exit = 2;
        try {
            if (args.length != 2 || !"127.0.0.1".equals(args[0])) throw new Gap("invalid_arguments");
            int port = Integer.parseInt(args[1]);
            if (port < 1 || port > 65535) throw new Gap("invalid_port");
            AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                    .filter(c -> "com.sun.jdi.SocketAttach".equals(c.name())).findFirst()
                    .orElseThrow(() -> new Gap("socket_attach_unavailable"));
            Map<String, Connector.Argument> arguments = connector.defaultArguments();
            arguments.get("hostname").setValue(args[0]);
            arguments.get("port").setValue(Integer.toString(port));
            arguments.get("timeout").setValue("5000");
            probe = new Pr64DataStoreProbe(connector.attach(arguments));
            Pr64DataStoreProbe owned = probe;
            shutdown = new Thread(owned::close, "pr64-jdi-detach");
            Runtime.getRuntime().addShutdownHook(shutdown);
            boolean supported = probe.vm.canGetInstanceInfo();
            emit(Map.of("phase", "capabilities", "canGetInstanceInfo", supported));
            if (!supported) throw new Gap("instance_info_unsupported");
            probe.preflight();
            emit(Map.of("phase", "ready"));
            String command = readCommand();
            if (command == null || "DISARM".equals(command)) {
                report = Map.of("phase", "disarmed", "status", "complete");
                exit = 0;
            } else if ("CAPTURE".equals(command)) {
                phase = "capture";
                probe.captureDeadline = System.nanoTime() + 15_000_000_000L;
                probe.vm.suspend();
                probe.suspended = true;
                Map<String, Object> state = probe.snapshot();
                report = Map.of("phase", phase, "status", "complete", "state", state);
                exit = 0;
            } else {
                throw new Gap("invalid_command");
            }
        } catch (Throwable error) {
            report = new LinkedHashMap<>();
            report.put("phase", phase);
            report.put("status", "gap");
            report.put("code", error instanceof Gap gap ? gap.code : "collector_exception");
            report.put("exceptionClass", error.getClass().getName());
            if (error instanceof Gap gap && !gap.details.isEmpty()) report.put("details", gap.details);
            if (probe != null && !probe.captured.isEmpty()) report.put("partialState", probe.captured);
        } finally {
            if (probe != null) {
                probe.close();
                if (!probe.cleanupErrors.isEmpty()) {
                    report = new LinkedHashMap<>(report);
                    report.put("primaryStatus", report.get("status"));
                    report.put("status", "gap");
                    report.putIfAbsent("code", "detach_failed");
                    report.put("cleanup", Map.of("status", "gap", "code", "detach_failed",
                            "exceptionClasses", List.copyOf(probe.cleanupErrors)));
                    exit = 2;
                }
            }
            if (shutdown != null) {
                try { Runtime.getRuntime().removeShutdownHook(shutdown); }
                catch (IllegalStateException ignored) { /* The shutdown hook already owns detach. */ }
            }
        }
        if (report != null) emit(report);
        System.exit(exit);
    }

    private static String readCommand() throws IOException {
        StringBuilder command = new StringBuilder(7);
        for (;;) {
            int value = System.in.read();
            if (value == -1) {
                if (command.length() == 0) return null;
                throw new Gap("unterminated_command");
            }
            if (value == '\n') return command.toString();
            if (value > 127 || command.length() == 7) throw new Gap("invalid_command");
            command.append((char) value);
        }
    }

    private synchronized void close() {
        if (closed) return;
        closed = true;
        if (suspended) {
            try { vm.resume(); }
            catch (Throwable error) { cleanupErrors.add(error.getClass().getName()); }
            suspended = false;
        }
        try { vm.dispose(); }
        catch (Throwable error) { cleanupErrors.add(error.getClass().getName()); }
    }

    private void preflight() {
        slot(APP, "preferencesDataStore$delegate", "Lkotlin/Lazy;");
        slot("kotlin.SynchronizedLazyImpl", "_value", "Ljava/lang/Object;");
        slot(PREF, "delegate", "Landroidx/datastore/core/DataStore;");
        slot(DS, "scope", "Lkotlinx/coroutines/CoroutineScope;");
        slot(DS, "writeActor", "Landroidx/datastore/core/SimpleActor;");
        slot(ACTOR, "scope", "Lkotlinx/coroutines/CoroutineScope;");
        slot(JOB, "_state$volatile", "Ljava/lang/Object;");
        slot(ABSTRACT, "context", "Lkotlin/coroutines/CoroutineContext;");
        slot(BASE, "completion", "Lkotlin/coroutines/Continuation;");
        slot(TEST, "composeRule", "Landroidx/compose/ui/test/junit4/AndroidComposeTestRule;");
        slot(UI + "junit4.AndroidComposeTestRule", "environment", "Landroidx/compose/ui/test/AndroidComposeUiTestEnvironment;");
        slot(UI + "AndroidComposeUiTestEnvironment", "compositionCoroutineDispatcher", "Lkotlinx/coroutines/test/TestDispatcher;");
        slot(UI + "AndroidComposeUiTestEnvironment", "mainClockImpl", "Landroidx/compose/ui/test/MainTestClockImpl;");
        slot(STANDARD, "scheduler", "Lkotlinx/coroutines/test/TestCoroutineScheduler;");
        slot(UI + "AbstractMainTestClock", "scheduler", "Lkotlinx/coroutines/test/TestCoroutineScheduler;");
        slot(SCHEDULER, "events", "Lkotlinx/coroutines/internal/ThreadSafeHeap;");
        slot(SCHEDULER, "currentTime", "J");
        slot(HEAP, "a", "[Lkotlinx/coroutines/internal/ThreadSafeHeapNode;");
        slot(HEAP, "_size$volatile", "I");
    }

    private void tick() {
        if (captureDeadline != 0 && (++reads > 4096 || System.nanoTime() > captureDeadline))
            throw new Gap("capture_budget_exhausted");
    }

    private ReferenceType type(String name) {
        tick();
        ReferenceType cached = types.get(name);
        if (cached != null) return cached;
        List<ReferenceType> found = vm.classesByName(name);
        boolean prepared = found.size() == 1 && found.get(0).isPrepared();
        if (found.size() != 1 || !prepared) throw new Gap("missing_or_ambiguous_type",
                Map.of("expectedClass", name, "observedClassCount", found.size(), "prepared", prepared));
        types.put(name, found.get(0));
        return found.get(0);
    }

    private Field slot(String owner, String name, String signature) {
        String key = owner + "#" + name;
        Field cached = fields.get(key);
        if (cached != null) {
            if (!cached.signature().equals(signature)) throw new Gap("field_signature_mismatch",
                    Map.of("expectedOwner", owner, "expectedField", name, "expectedSignature", signature,
                            "observedFieldCount", 1, "observedSignatures", List.of(cached.signature())));
            return cached;
        }
        ReferenceType declaring;
        try {
            declaring = type(owner);
        } catch (Gap error) {
            Map<String, Object> details = new LinkedHashMap<>(error.details);
            details.put("expectedOwner", owner);
            details.put("expectedField", name);
            details.put("expectedSignature", signature);
            throw new Gap(error.code, details);
        }
        List<Field> found = declaring.fields().stream().filter(f -> name.equals(f.name())).toList();
        if (found.size() != 1 || !signature.equals(found.get(0).signature()) || found.get(0).isStatic())
            throw new Gap("field_shape_mismatch",
                    Map.of("expectedOwner", owner, "expectedField", name, "expectedSignature", signature,
                            "observedFieldCount", found.size(),
                            "observedSignatures", found.stream().map(Field::signature).toList(),
                            "observedStatic", found.size() == 1 && found.get(0).isStatic()));
        fields.put(key, found.get(0));
        return found.get(0);
    }

    private Value value(ObjectReference object, String owner, String name, String signature) {
        tick();
        return object.getValue(slot(owner, name, signature));
    }

    private ObjectReference object(Value value) {
        if (!(value instanceof ObjectReference object) || value instanceof StringReference)
            throw new Gap("unexpected_reference_shape");
        return object;
    }

    private ObjectReference ref(ObjectReference object, String owner, String name, String signature) {
        return object(value(object, owner, name, signature));
    }

    private int integer(ObjectReference object, String owner, String name) {
        Value value = value(object, owner, name, "I");
        if (!(value instanceof IntegerValue integer)) throw new Gap("unexpected_integer_shape");
        return integer.value();
    }

    private long number(ObjectReference object, String owner, String name) {
        Value value = value(object, owner, name, "J");
        if (!(value instanceof LongValue number)) throw new Gap("unexpected_long_shape");
        return number.value();
    }

    private boolean flag(ObjectReference object, String owner, String name) {
        Value value = value(object, owner, name, "Z");
        if (!(value instanceof BooleanValue flag)) throw new Gap("unexpected_boolean_shape");
        return flag.value();
    }

    private static boolean same(ObjectReference a, ObjectReference b) {
        return a.uniqueID() == b.uniqueID();
    }

    private boolean isA(ObjectReference object, String name) {
        ReferenceType type = object.referenceType();
        if (name.equals(type.name())) return true;
        if (!(type instanceof ClassType current)) return false;
        for (int depth = 0; current != null; depth++, current = current.superclass()) {
            tick();
            if (depth == LIMIT) throw new Gap("hierarchy_overflow");
            if (name.equals(current.name())) return true;
        }
        return false;
    }

    private List<ObjectReference> instances(String name) {
        tick();
        List<ReferenceType> loaded = vm.classesByName(name);
        if (loaded.isEmpty()) return List.of();
        if (loaded.size() != 1) throw new Gap("ambiguous_classloader",
                Map.of("expectedClass", name, "observedClassCount", loaded.size()));
        List<ObjectReference> found = loaded.get(0).instances(LIMIT + 1L);
        if (found.size() > LIMIT) throw new Gap("instance_overflow",
                Map.of("expectedClass", name, "observedCandidateCount", found.size(),
                        "candidateLimit", LIMIT, "countIsLowerBound", true));
        return found;
    }

    private static ObjectReference unique(List<ObjectReference> found, String role, String relationship) {
        if (found.size() != 1) throw new Gap("missing_or_ambiguous_identity",
                Map.of("bindingRole", role, "observedCandidateCount", found.size(),
                        "expectedRelationship", relationship));
        return found.get(0);
    }

    private record PumpFrame(ThreadReference thread, ObjectReference scheduler, String owner, String method) {}

    private void pumpUnavailable(String code, Map<String, Object> details) {
        if (pumpGaps.size() == GRAPH_LIMIT) throw new Gap("pump_binding_gap_overflow");
        Map<String, Object> gap = new LinkedHashMap<>(details);
        gap.put("code", code);
        gap.put("bindingRole", "composition_pump_activity");
        pumpGaps.add(gap);
    }

    private void pumpMethods(String owner, Map<String, String> expected) {
        ReferenceType declaring;
        try {
            declaring = type(owner);
        } catch (Gap error) {
            if (error.code.equals("capture_budget_exhausted")) throw error;
            pumpUnavailable(error.code, error.details);
            return;
        }
        List<Method> methods = declaring.methods();
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            tick();
            List<Method> found = methods.stream().filter(method -> method.name().equals(entry.getKey())).toList();
            if (found.size() != 1 || !found.get(0).signature().equals(entry.getValue()) || found.get(0).isStatic())
                pumpUnavailable("pump_method_schema_unavailable",
                        Map.of("expectedOwner", owner, "expectedMethod", entry.getKey(),
                                "expectedSignature", entry.getValue(), "observedMethodCount", found.size(),
                                "observedSignatures", found.stream().map(Method::signature).toList()));
        }
    }

    private ObjectReference observePump(ThreadReference thread, StackFrame frame, String owner, String name) {
        boolean schedulerFrame = SCHEDULER.equals(owner);
        boolean dispatcherFrame = TEST_DISPATCHER.equals(owner) && name.startsWith("processEvent");
        if (!dispatcherFrame && (!schedulerFrame || !(name.startsWith("advance")
                || name.equals("runCurrent") || name.startsWith("tryRunNextTaskUnless")))) return null;
        Method method = frame.location().method();
        // Pinned static foreground predicates are not scheduler pumps.
        if (schedulerFrame && name.equals("advanceUntilIdle$lambda$7") && method.isStatic()) return null;
        String signature = schedulerFrame ? PUMP_METHODS.get(name)
                : name.equals(PROCESS_EVENT) ? "(Ljava/lang/Object;)V" : null;
        if (signature == null || !signature.equals(method.signature()) || method.isStatic()) {
            pumpUnavailable("pump_frame_method_unavailable",
                    Map.of("expectedOwner", owner, "observedMethod", name,
                            "observedSignature", method.signature()));
            return null;
        }
        ObjectReference receiver = frame.thisObject();
        if (receiver == null) {
            pumpUnavailable("pump_frame_receiver_unavailable",
                    Map.of("expectedOwner", owner, "expectedMethod", name,
                            "expectedRelationship", "identity-bound receiver of the executing pinned pump frame"));
            return null;
        }
        ObjectReference scheduler;
        if (schedulerFrame) {
            scheduler = receiver;
        } else {
            if (!receiver.referenceType().name().equals(STANDARD)) {
                pumpUnavailable("pump_dispatcher_receiver_unavailable",
                        Map.of("expectedReceiverClass", STANDARD, "observedReceiverClass", receiver.referenceType().name(),
                                "expectedMethod", PROCESS_EVENT));
                return null;
            }
            try {
                scheduler = ref(receiver, STANDARD, "scheduler", "Lkotlinx/coroutines/test/TestCoroutineScheduler;");
            } catch (Gap error) {
                if (error.code.equals("capture_budget_exhausted")) throw error;
                pumpUnavailable(error.code, error.details);
                return null;
            }
        }
        if (!scheduler.referenceType().name().equals(SCHEDULER)) {
            pumpUnavailable("pump_scheduler_receiver_unavailable",
                    Map.of("expectedReceiverClass", SCHEDULER, "observedReceiverClass", scheduler.referenceType().name()));
            return null;
        }
        if (pumpFrames.size() == GRAPH_LIMIT) throw new Gap("pump_frame_overflow");
        pumpFrames.add(new PumpFrame(thread, scheduler, owner, name));
        return scheduler;
    }

    private record Roots(ThreadReference thread, ObjectReference test, ObjectReference caller,
                         ObjectReference outerScheduler) {}

    private Roots roots() throws IncompatibleThreadStateException {
        pumpMethods(SCHEDULER, PUMP_METHODS);
        pumpMethods(TEST_DISPATCHER, Map.of(PROCESS_EVENT, "(Ljava/lang/Object;)V"));
        List<Roots> found = new ArrayList<>();
        List<ThreadReference> threads = vm.allThreads();
        if (threads.size() > GRAPH_LIMIT) throw new Gap("thread_overflow");
        for (ThreadReference thread : threads) {
            tick();
            int count = thread.frameCount();
            if (count > GRAPH_LIMIT) throw new Gap("frame_overflow");
            ObjectReference caller = null;
            ObjectReference test = null;
            Map<Long, ObjectReference> schedulers = new LinkedHashMap<>();
            for (StackFrame frame : thread.frames(0, count)) {
                String owner = frame.location().declaringType().name();
                String method = frame.location().method().name();
                ObjectReference pumpScheduler = observePump(thread, frame, owner, method);
                if (BLOCKING.equals(owner) && "joinBlocking".equals(method) && caller == null)
                    caller = frame.thisObject();
                if (TEST.equals(owner) && "cleanup".equals(method) && caller != null) {
                    if (test != null) throw new Gap("ambiguous_cleanup_frame");
                    test = frame.thisObject();
                    if (test == null || !same(ref(caller, BLOCKING, "blockedThread", "Ljava/lang/Thread;"), thread))
                        throw new Gap("caller_thread_mismatch");
                }
                if (SCHEDULER.equals(owner) && method.equals("tryRunNextTaskUnless$kotlinx_coroutines_test")
                        && pumpScheduler != null) schedulers.put(pumpScheduler.uniqueID(), pumpScheduler);
            }
            if (test != null) found.add(new Roots(thread, test, caller,
                    unique(new ArrayList<>(schedulers.values()), "outer_run_test_scheduler",
                            "one scheduler receiver on the current cleanup thread's tryRunNextTaskUnless frame")));
        }
        if (found.size() != 1) throw new Gap("missing_or_ambiguous_cleanup_thread",
                Map.of("bindingRole", "cleanup_thread", "observedCandidateCount", found.size(),
                        "expectedRelationship", "one live cleanup frame and BlockingCoroutine whose blockedThread matches that thread"));
        return found.get(0);
    }

    private Map<String, Object> identity(ObjectReference object) {
        return Map.of("id", object.uniqueID(), "type", object.referenceType().name());
    }

    private ObjectReference state(ObjectReference job) {
        Value state = value(job, JOB, "_state$volatile", "Ljava/lang/Object;");
        if (!(state instanceof ObjectReference object)) throw new Gap("unknown_job_state_shape");
        return object;
    }

    private boolean incomplete(ObjectReference state) {
        String name = state.referenceType().name();
        // Do not help an in-flight descriptor: target mutation is forbidden.
        if (isA(state, "kotlinx.coroutines.internal.OpDescriptor")
                || isA(state, "kotlinx.coroutines.internal.AtomicOp"))
            throw new Gap("unresolved_job_state_operation",
                    Map.of("expectedOwner", JOB, "expectedField", "_state$volatile", "observedStateClass", name));
        boolean known = name.equals("kotlinx.coroutines.Empty") || name.equals("kotlinx.coroutines.NodeList")
                || name.equals("kotlinx.coroutines.InactiveNodeList") || name.equals(JOB + "$Finishing")
                || isA(state, "kotlinx.coroutines.JobNode");
        if (!known && state.referenceType() instanceof ClassType type && type.allInterfaces().stream()
                .anyMatch(item -> item.name().equals("kotlinx.coroutines.Incomplete")))
            throw new Gap("unknown_incomplete_job_state",
                    Map.of("expectedOwner", JOB, "expectedField", "_state$volatile", "observedStateClass", name));
        return known;
    }

    private Map<String, Object> jobState(ObjectReference job) {
        ObjectReference state = state(job);
        Map<String, Object> result = new LinkedHashMap<>(identity(job));
        String name = state.referenceType().name();
        result.put("stateType", name);
        result.put("completed", !incomplete(state));
        if (name.equals("kotlinx.coroutines.Empty")) result.put("active", flag(state, name, "isActive"));
        if (name.equals(JOB + "$Finishing")) {
            result.put("completing", integer(state, name, "_isCompleting$volatile") != 0);
            result.put("cancelling", value(state, name, "_rootCause$volatile", "Ljava/lang/Object;") != null);
        }
        return result;
    }

    private List<ObjectReference> nodes(ObjectReference job) {
        ObjectReference state = state(job);
        String name = state.referenceType().name();
        if (!incomplete(state) || name.equals("kotlinx.coroutines.Empty")) return List.of();
        if (isA(state, "kotlinx.coroutines.JobNode")) return List.of(state);
        ObjectReference head = state;
        if (name.equals(JOB + "$Finishing") || name.equals("kotlinx.coroutines.InactiveNodeList"))
            head = ref(state, name, "list", "Lkotlinx/coroutines/NodeList;");
        List<ObjectReference> found = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ObjectReference next = ref(head, LINK, "_next$volatile", "Ljava/lang/Object;");
        while (!same(next, head)) {
            if (!visited.add(next.uniqueID()) || visited.size() > GRAPH_LIMIT) throw new Gap("job_list_overflow_or_cycle");
            if (next.referenceType().name().equals("kotlinx.coroutines.internal.Removed")) {
                next = ref(next, "kotlinx.coroutines.internal.Removed", "ref", "Lkotlinx/coroutines/internal/LockFreeLinkedListNode;");
                continue;
            }
            if (!isA(next, "kotlinx.coroutines.JobNode")) throw new Gap("unresolved_job_list_operation");
            found.add(next);
            next = ref(next, LINK, "_next$volatile", "Ljava/lang/Object;");
        }
        return found;
    }

    private record JobGraph(Map<Long, ObjectReference> jobs, List<ObjectReference> awaiters) {}

    private JobGraph jobs(ObjectReference root) {
        Map<Long, ObjectReference> found = new LinkedHashMap<>();
        List<ObjectReference> awaiters = new ArrayList<>();
        ArrayDeque<ObjectReference> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            ObjectReference job = queue.remove();
            if (found.putIfAbsent(job.uniqueID(), job) != null) continue;
            if (found.size() > GRAPH_LIMIT) throw new Gap("job_graph_overflow");
            for (ObjectReference node : nodes(job)) {
                String name = node.referenceType().name();
                if (name.equals(CHILD)) queue.add(ref(node, CHILD, "childJob", "Lkotlinx/coroutines/ChildJob;"));
                if (name.equals(WAITER)) {
                    ObjectReference waiter = ref(node, WAITER, "child", "Lkotlinx/coroutines/CancellableContinuationImpl;");
                    if (waiter.referenceType().name().equals(AWAIT)) awaiters.add(waiter);
                }
            }
        }
        return new JobGraph(found, awaiters);
    }

    private List<ObjectReference> path(ObjectReference start) {
        List<ObjectReference> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ObjectReference current = start;
        while (current != null) {
            tick();
            if (!seen.add(current.uniqueID()) || found.size() == GRAPH_LIMIT) throw new Gap("continuation_cycle_or_overflow");
            found.add(current);
            String name = current.referenceType().name();
            Value next;
            if (name.equals(DC)) next = value(current, DC, "continuation", "Lkotlin/coroutines/Continuation;");
            else if (name.equals(APPLY + "$SendApplyContinuation"))
                next = value(current, name, "continuation", "Lkotlin/coroutines/Continuation;");
            else if (isA(current, CCI)) next = value(current, CCI, "delegate", "Lkotlin/coroutines/Continuation;");
            else if (isA(current, BASE)) next = value(current, BASE, "completion", "Lkotlin/coroutines/Continuation;");
            else if (isA(current, SCOPE)) next = value(current, SCOPE, "uCont", "Lkotlin/coroutines/Continuation;");
            else if (isA(current, ABSTRACT)) break;
            else throw new Gap("unknown_continuation_wrapper");
            current = next == null ? null : object(next);
        }
        return found;
    }

    private static boolean contains(List<ObjectReference> path, ObjectReference object) {
        return path.stream().anyMatch(item -> same(item, object));
    }

    private ObjectReference transform(ObjectReference object) {
        Set<Long> seen = new HashSet<>();
        for (int depth = 0; depth < 4; depth++) {
            String name = object.referenceType().name();
            if (!name.equals(PREF_TRANSFORM) && !name.equals(EDIT)) return object;
            if (!seen.add(object.uniqueID())) throw new Gap("transform_cycle");
            object = ref(object, name, "$transform", "Lkotlin/jvm/functions/Function2;");
        }
        throw new Gap("transform_wrapper_overflow");
    }

    private ObjectReference restore(JobGraph caller, ObjectReference root, ObjectReference store) {
        List<ObjectReference> candidates = restoreCandidates(caller.awaiters, root, store);
        if (candidates.isEmpty()) candidates = restoreCandidates(instances(AWAIT), root, store);
        return unique(candidates, "restore_acknowledgement",
                "one " + AWAIT + ".job bound to the current cleanup caller, actual store, await label1 and reader-update transform");
    }

    private List<ObjectReference> restoreCandidates(List<ObjectReference> awaiters, ObjectReference root, ObjectReference store) {
        List<ObjectReference> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ObjectReference awaiter : awaiters) {
            if (!seen.add(awaiter.uniqueID())) continue;
            List<ObjectReference> chain = path(awaiter);
            if (!contains(chain, root)) continue;
            for (ObjectReference item : chain) {
                if (!item.referenceType().name().equals(DS + "$updateData$2")) continue;
                if (same(ref(item, DS + "$updateData$2", "this$0", "Landroidx/datastore/core/DataStoreImpl;"), store)
                        && integer(item, DS + "$updateData$2", "label") == 1) {
                    ObjectReference operation = transform(ref(item, DS + "$updateData$2", "$transform", "Lkotlin/jvm/functions/Function2;"));
                    if (!operation.referenceType().name().equals("org.tsuyomi.core.preferences.PortableReaderPreferencesRepository$update$2"))
                        throw new Gap("cleanup_not_reader_restore");
                    found.add(ref(awaiter, AWAIT, "job", "Lkotlinx/coroutines/JobSupport;"));
                }
            }
        }
        return found;
    }

    private ObjectReference context(ObjectReference scope) {
        if (isA(scope, ABSTRACT)) return ref(scope, ABSTRACT, "context", "Lkotlin/coroutines/CoroutineContext;");
        return ref(scope, "kotlinx.coroutines.internal.ContextScope", "coroutineContext", "Lkotlin/coroutines/CoroutineContext;");
    }

    private List<ObjectReference> elements(ObjectReference context) {
        List<ObjectReference> elements = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        while (context.referenceType().name().equals("kotlin.coroutines.CombinedContext")) {
            if (!seen.add(context.uniqueID()) || elements.size() == LIMIT) throw new Gap("context_overflow_or_cycle");
            elements.add(ref(context, "kotlin.coroutines.CombinedContext", "element", "Lkotlin/coroutines/CoroutineContext$Element;"));
            context = ref(context, "kotlin.coroutines.CombinedContext", "left", "Lkotlin/coroutines/CoroutineContext;");
        }
        elements.add(context);
        return elements;
    }

    private ObjectReference contextJob(ObjectReference context, String role) {
        return unique(elements(context).stream().filter(item -> isA(item, JOB)).toList(), role,
                "one JobSupport element in the supplied flattened CoroutineContext");
    }

    private record Dispatcher(ObjectReference interceptor, ObjectReference dispatcher, ObjectReference scheduler,
                              ObjectReference frame) {}

    private Dispatcher dispatcher(ObjectReference context, String role) {
        List<ObjectReference> found = elements(context).stream()
                .filter(item -> isA(item, "kotlin.coroutines.ContinuationInterceptor")
                        || item.referenceType() instanceof ClassType c && c.allInterfaces().stream()
                        .anyMatch(i -> i.name().equals("kotlin.coroutines.ContinuationInterceptor"))).toList();
        ObjectReference interceptor = unique(found, role + "_interceptor",
                "one ContinuationInterceptor element in the supplied flattened CoroutineContext");
        ObjectReference dispatcher = interceptor;
        if (dispatcher.referenceType().name().equals(APPLY))
            dispatcher = ref(dispatcher, APPLY, "delegate", "Lkotlin/coroutines/ContinuationInterceptor;");
        ObjectReference frame = null;
        if (dispatcher.referenceType().name().equals(FRAME)) {
            frame = dispatcher;
            dispatcher = ref(frame, FRAME, "parentDispatcher", "Lkotlinx/coroutines/CoroutineDispatcher;");
        }
        ObjectReference scheduler = dispatcher.referenceType().name().equals(STANDARD)
                ? ref(dispatcher, STANDARD, "scheduler", "Lkotlinx/coroutines/test/TestCoroutineScheduler;") : null;
        return new Dispatcher(interceptor, dispatcher, scheduler, frame);
    }

    private record Consumer(ObjectReference continuation, ObjectReference actorJob, ObjectReference ack,
                            ObjectReference update) {}

    private Consumer consumer(ObjectReference store, ObjectReference actor, JobGraph storeJobs) {
        List<ObjectReference> handles = new ArrayList<>();
        Map<Long, ObjectReference> actorJobs = new HashMap<>();
        for (ObjectReference handle : instances(DS + "$handleUpdate$1")) {
            if (!same(ref(handle, DS + "$handleUpdate$1", "this$0", "Landroidx/datastore/core/DataStoreImpl;"), store)) continue;
            List<ObjectReference> chain = path(handle);
            boolean actorMatch = false;
            ObjectReference job = null;
            for (ObjectReference item : chain) {
                if (item.referenceType().name().equals(ACTOR + "$offer$2"))
                    actorMatch = same(ref(item, ACTOR + "$offer$2", "this$0", "Landroidx/datastore/core/SimpleActor;"), actor);
                if (isA(item, ABSTRACT) && !isA(item, SCOPE)) job = item;
            }
            if (!actorMatch || job == null || !storeJobs.jobs.containsKey(job.uniqueID()) || !incomplete(state(job))) continue;
            boolean linked = false;
            for (ObjectReference child : jobs(job).jobs.values()) {
                if (isA(child, SCOPE) && contains(path(ref(child, SCOPE, "uCont", "Lkotlin/coroutines/Continuation;")), handle)) linked = true;
            }
            if (linked) { handles.add(handle); actorJobs.put(handle.uniqueID(), job); }
        }
        ObjectReference handle = unique(handles, "live_consumer",
                "one " + DS + "$handleUpdate$1 bound to the actual writeActor, incomplete actor Job and current ScopeCoroutine child");
        ObjectReference ack = ref(handle, DS + "$handleUpdate$1", "L$0", "Ljava/lang/Object;");
        if (!isA(ack, JOB)) throw new Gap("consumer_ack_shape");
        List<ObjectReference> updates = new ArrayList<>();
        for (ObjectReference inner : instances(DS + "$handleUpdate$2$1")) {
            if (!same(ref(inner, DS + "$handleUpdate$2$1", "$this_runCatching", "Landroidx/datastore/core/DataStoreImpl;"), store)) continue;
            ObjectReference update = ref(inner, DS + "$handleUpdate$2$1", "$update", "Landroidx/datastore/core/Message$Update;");
            if (same(ref(update, UPDATE, "ack", "Lkotlinx/coroutines/CompletableDeferred;"), ack) && contains(path(inner), handle)) updates.add(update);
        }
        return new Consumer(handle, actorJobs.get(handle.uniqueID()), ack,
                unique(updates, "consumer_message", "one Message.Update whose ack and live continuation chain match the selected consumer"));
    }

    private ObjectReference stage(ObjectReference store, Consumer consumer) {
        List<ObjectReference> found = new ArrayList<>();
        for (ObjectReference item : instances(DS + "$transformAndWrite$2")) {
            if (same(ref(item, DS + "$transformAndWrite$2", "this$0", "Landroidx/datastore/core/DataStoreImpl;"), store)
                    && contains(path(item), consumer.continuation)) found.add(item);
        }
        return unique(found, "consumer_transform_stage",
                "one " + DS + "$transformAndWrite$2 bound to the actual store and selected live consumer continuation");
    }

    private List<Map<String, Object>> queued(ObjectReference scheduler, ObjectReference stage) {
        ObjectReference heap = ref(scheduler, SCHEDULER, "events", "Lkotlinx/coroutines/internal/ThreadSafeHeap;");
        int size = integer(heap, HEAP, "_size$volatile");
        if (size < 0 || size > LIMIT) throw new Gap("scheduler_queue_overflow");
        if (size == 0) return List.of();
        ArrayReference array = (ArrayReference) ref(heap, HEAP, "a", "[Lkotlinx/coroutines/internal/ThreadSafeHeapNode;");
        if (size > array.length()) throw new Gap("scheduler_heap_shape");
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Value entry : array.getValues(0, size)) {
            ObjectReference event = object(entry);
            if (!event.referenceType().name().equals(EVENT)) throw new Gap("unknown_scheduler_event");
            ObjectReference marker = ref(event, EVENT, "marker", "Ljava/lang/Object;");
            if (contains(path(marker), stage)) {
                ObjectReference dispatcher = ref(event, EVENT, "dispatcher", "Lkotlinx/coroutines/test/TestDispatcher;");
                if (!dispatcher.referenceType().name().equals(STANDARD)
                        || !same(ref(dispatcher, STANDARD, "scheduler", "Lkotlinx/coroutines/test/TestCoroutineScheduler;"), scheduler))
                    throw new Gap("scheduler_event_identity_mismatch");
                matches.add(Map.of("event", identity(event), "marker", identity(marker),
                        "time", number(event, EVENT, "time"), "scheduler", identity(scheduler)));
            }
        }
        return matches;
    }

    private List<Map<String, Object>> staged(ObjectReference frame, ObjectReference stage) {
        if (frame == null) return List.of();
        ObjectReference deque = ref(frame, FRAME, "toRunTrampolined", "Lkotlin/collections/ArrayDeque;");
        int size = integer(deque, "kotlin.collections.ArrayDeque", "size");
        if (size < 0 || size > LIMIT) throw new Gap("frame_queue_overflow");
        if (size == 0) return List.of();
        int head = integer(deque, "kotlin.collections.ArrayDeque", "head");
        ArrayReference array = (ArrayReference) ref(deque, "kotlin.collections.ArrayDeque", "elementData", "[Ljava/lang/Object;");
        if (size > array.length() || head < 0 || head >= array.length()) throw new Gap("frame_queue_shape");
        List<Map<String, Object>> matches = new ArrayList<>();
        for (int offset = 0; offset < size; offset++) {
            ObjectReference marker = object(array.getValue((head + offset) % array.length()));
            if (contains(path(marker), stage)) matches.add(identity(marker));
        }
        return matches;
    }

    private Map<String, Object> snapshot() throws IncompatibleThreadStateException {
        Roots roots = roots();
        captured.put("thread", identity(roots.thread));
        captured.put("test", identity(roots.test));
        captured.put("restoreCaller", jobState(roots.caller));
        ObjectReference app = unique(instances(APP), "shared_application", "one " + APP + " in the guarded target VM");
        ObjectReference lazy = ref(app, APP, "preferencesDataStore$delegate", "Lkotlin/Lazy;");
        ObjectReference preferenceStore = ref(lazy, "kotlin.SynchronizedLazyImpl", "_value", "Ljava/lang/Object;");
        ObjectReference store = ref(preferenceStore, PREF, "delegate", "Landroidx/datastore/core/DataStore;");
        if (!store.referenceType().name().equals(DS)) throw new Gap("unexpected_store_type");
        ObjectReference actor = ref(store, DS, "writeActor", "Landroidx/datastore/core/SimpleActor;");
        ObjectReference storeScope = ref(store, DS, "scope", "Lkotlinx/coroutines/CoroutineScope;");
        ObjectReference storeJob = contextJob(context(storeScope), "shared_store_scope_job");
        captured.put("store", identity(store));
        captured.put("storeJob", jobState(storeJob));
        ObjectReference restore = restore(jobs(roots.caller), roots.caller, store);
        captured.put("restoreAck", jobState(restore));
        Consumer consumer = consumer(store, actor, jobs(storeJob));
        captured.put("consumerJob", jobState(consumer.actorJob));
        captured.put("consumerAck", jobState(consumer.ack));
        ObjectReference stage = stage(store, consumer);
        int label = integer(stage, DS + "$transformAndWrite$2", "label");
        String stageName = switch (label) {
            case 1 -> "read";
            case 2 -> "caller_context_transform";
            case 3 -> "write";
            default -> throw new Gap("unknown_transform_stage");
        };
        ObjectReference callerContext = ref(consumer.update, UPDATE, "callerContext", "Lkotlin/coroutines/CoroutineContext;");
        if (!same(callerContext, ref(stage, DS + "$transformAndWrite$2", "$callerContext", "Lkotlin/coroutines/CoroutineContext;")))
            throw new Gap("consumer_context_identity_mismatch");
        Dispatcher dispatcher = dispatcher(callerContext, "consumer_caller");
        ObjectReference rule = ref(roots.test, TEST, "composeRule", "Landroidx/compose/ui/test/junit4/AndroidComposeTestRule;");
        ObjectReference environment = ref(rule, UI + "junit4.AndroidComposeTestRule", "environment", "Landroidx/compose/ui/test/AndroidComposeUiTestEnvironment;");
        ObjectReference compositionDispatcher = ref(environment, UI + "AndroidComposeUiTestEnvironment", "compositionCoroutineDispatcher", "Lkotlinx/coroutines/test/TestDispatcher;");
        ObjectReference compositionScheduler = ref(compositionDispatcher, STANDARD, "scheduler", "Lkotlinx/coroutines/test/TestCoroutineScheduler;");
        ObjectReference clock = ref(environment, UI + "AndroidComposeUiTestEnvironment", "mainClockImpl", "Landroidx/compose/ui/test/MainTestClockImpl;");
        if (!same(compositionScheduler, ref(clock, UI + "AbstractMainTestClock", "scheduler", "Lkotlinx/coroutines/test/TestCoroutineScheduler;")))
            throw new Gap("composition_clock_identity_mismatch");
        List<Map<String, Object>> compositionPumps = pumpFrames.stream()
                .filter(pump -> same(pump.scheduler, compositionScheduler))
                .map(pump -> Map.<String, Object>of("thread", identity(pump.thread),
                        "scheduler", identity(pump.scheduler), "owner", pump.owner, "method", pump.method)).toList();
        boolean pumpAvailable = pumpGaps.isEmpty();
        captured.put("compositionPump", Map.of("available", pumpAvailable,
                "scheduler", identity(compositionScheduler), "executingPumpFrames", compositionPumps,
                "bindingGaps", List.copyOf(pumpGaps), "temporalDeadlockProof", false));
        List<Map<String, Object>> queue = queued(compositionScheduler, stage);
        List<Map<String, Object>> staging = staged(dispatcher.frame, stage);
        boolean pending = incomplete(state(restore));
        boolean different = !same(restore, consumer.ack);
        boolean onComposition = dispatcher.scheduler != null && same(dispatcher.scheduler, compositionScheduler);
        Map<String, Object> result = captured;
        result.put("consumerCallerJob", jobState(contextJob(callerContext, "consumer_caller_job")));
        result.put("consumerTransform", identity(transform(ref(consumer.update, UPDATE, "transform", "Lkotlin/jvm/functions/Function2;"))));
        result.put("consumerStage", stageName);
        result.put("consumerContinuation", identity(stage));
        result.put("consumerLabel", label);
        result.put("consumerInterceptor", identity(dispatcher.interceptor));
        result.put("consumerDispatcher", identity(dispatcher.dispatcher));
        result.put("compositionScheduler", identity(compositionScheduler));
        result.put("compositionCurrentTime", number(compositionScheduler, SCHEDULER, "currentTime"));
        if (dispatcher.scheduler != null) result.put("consumerScheduler", identity(dispatcher.scheduler));
        result.put("outerRunTestContext", identity(ref(environment, UI + "AndroidComposeUiTestEnvironment", "runTestContext", "Lkotlin/coroutines/CoroutineContext;")));
        result.put("outerScheduler", identity(roots.outerScheduler));
        result.put("compositionEqualsOuterScheduler", same(compositionScheduler, roots.outerScheduler));
        Dispatcher restoreDispatcher = dispatcher(context(roots.caller), "restore_caller");
        result.put("restoreCallerDispatcher", identity(restoreDispatcher.dispatcher));
        result.put("compositionQueueMatches", queue);
        result.put("frameQueueMatches", staging);
        if (dispatcher.frame != null) result.put("frameDeferring", flag(dispatcher.frame, FRAME, "isDeferringContinuations"));
        result.put("consumerEqualsRestore", !different);
        result.put("headOfLineClassificationAvailable", pumpAvailable);
        result.put("headOfLineOnCompositionQueue", pending && different && label == 2 && onComposition
                && pumpAvailable && compositionPumps.isEmpty() && (!queue.isEmpty() || !staging.isEmpty()));
        return result;
    }

    private static void emit(Map<String, Object> event) {
        StringBuilder output = new StringBuilder();
        json(output, event);
        System.out.println(output);
        System.out.flush();
    }

    private static void json(StringBuilder output, Object value) {
        if (value instanceof String text) {
            output.append('"');
            for (int index = 0; index < text.length(); index++) {
                char c = text.charAt(index);
                if (c == '"' || c == '\\') output.append('\\').append(c);
                else if (c < 32) {
                    output.append("\\u");
                    String hex = Integer.toHexString(c);
                    output.append("0000", 0, 4 - hex.length()).append(hex);
                } else output.append(c);
            }
            output.append('"');
        } else if (value instanceof Boolean flag) output.append(flag.booleanValue());
        else if (value instanceof Integer integer) output.append(integer.intValue());
        else if (value instanceof Long number) output.append(number.longValue());
        else if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean comma = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) throw new Gap("unsafe_json_key");
                if (comma) output.append(',');
                json(output, entry.getKey()); output.append(':'); json(output, entry.getValue());
                comma = true;
            }
            output.append('}');
        } else if (value instanceof List<?> list) {
            output.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index != 0) output.append(',');
                json(output, list.get(index));
            }
            output.append(']');
        } else throw new Gap("unsafe_json_value");
    }
}
