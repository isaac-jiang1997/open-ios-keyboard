#include <jni.h>
#include <rime_api.h>

#include <algorithm>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

namespace {
constexpr char kCandidateSeparator = '\x1f';
std::mutex g_mutex;
RimeApi* g_rime = nullptr;
RimeSessionId g_session = 0;
bool g_initialized = false;

void append_candidate(std::vector<std::string>& candidates,
                      std::vector<std::string>& seen_text,
                      const RimeCandidate& candidate) {
    if (!candidate.text) {
        return;
    }
    std::string text(candidate.text);
    if (std::find(seen_text.begin(), seen_text.end(), text) != seen_text.end()) {
        return;
    }
    seen_text.emplace_back(text);
    std::string item(text);
    item.push_back(kCandidateSeparator);
    if (candidate.comment) {
        item.append(candidate.comment);
    }
    candidates.emplace_back(std::move(item));
}

std::string to_string(JNIEnv* env, jstring value) {
    if (!value) {
        return "";
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars ? chars : "";
    if (chars) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return out;
}

jobjectArray to_java_array(JNIEnv* env, const std::vector<std::string>& values) {
    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(
            static_cast<jsize>(values.size()), string_class, nullptr);
    for (jsize i = 0; i < static_cast<jsize>(values.size()); ++i) {
        jstring item = env->NewStringUTF(values[static_cast<size_t>(i)].c_str());
        env->SetObjectArrayElement(array, i, item);
        env->DeleteLocalRef(item);
    }
    return array;
}

std::string take_commit() {
    if (!g_rime || !g_session) {
        return "";
    }
    RIME_STRUCT(RimeCommit, commit);
    std::string text;
    if (g_rime->get_commit(g_session, &commit) && commit.text) {
        text = commit.text;
        g_rime->free_commit(&commit);
    }
    return text;
}

std::vector<std::string> snapshot(const std::string& commit_text) {
    std::vector<std::string> result;
    result.push_back(commit_text);

    std::string preedit;
    std::vector<std::string> candidates;
    if (g_rime && g_session) {
        RIME_STRUCT(RimeContext, context);
        if (g_rime->get_context(g_session, &context)) {
            if (context.composition.preedit) {
                preedit = context.composition.preedit;
            }
            std::vector<std::string> seen_text;
            RimeCandidateListIterator iterator = {0};
            if (g_rime->candidate_list_begin(g_session, &iterator)) {
                while (g_rime->candidate_list_next(&iterator)) {
                    append_candidate(candidates, seen_text, iterator.candidate);
                    if (candidates.size() >= 80) {
                        break;
                    }
                }
                g_rime->candidate_list_end(&iterator);
            }
            for (int i = 0; i < context.menu.num_candidates; ++i) {
                append_candidate(candidates, seen_text, context.menu.candidates[i]);
            }
            g_rime->free_context(&context);
        }
        if (preedit.empty()) {
            const char* raw_input = g_rime->get_input(g_session);
            if (raw_input) {
                preedit = raw_input;
            }
        }
    }

    result.push_back(preedit);
    result.insert(result.end(), candidates.begin(), candidates.end());
    return result;
}

jobjectArray snapshot_array(JNIEnv* env, const std::string& commit_text = "") {
    return to_java_array(env, snapshot(commit_text));
}

bool ensure_session() {
    if (!g_rime || !g_initialized) {
        return false;
    }
    if (!g_session) {
        g_session = g_rime->create_session();
    }
    return g_session != 0;
}
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_openkeyboard_ioskeyboard_RimeNativeBridge_nativeInitialize(
        JNIEnv* env, jclass, jstring shared_dir, jstring user_dir) {
    std::lock_guard<std::mutex> lock(g_mutex);
    std::string shared = to_string(env, shared_dir);
    std::string user = to_string(env, user_dir);
    if (shared.empty() || user.empty()) {
        return JNI_FALSE;
    }

    g_rime = rime_get_api();
    if (!g_rime) {
        return JNI_FALSE;
    }

    if (g_initialized) {
        return ensure_session() ? JNI_TRUE : JNI_FALSE;
    }

    static const char* modules[] = {"default", nullptr};
    RIME_STRUCT(RimeTraits, traits);
    traits.shared_data_dir = shared.c_str();
    traits.user_data_dir = user.c_str();
    traits.distribution_name = "Open iOS Keyboard";
    traits.distribution_code_name = "openphone";
    traits.distribution_version = "1.0";
    traits.app_name = "rime.openphone";
    traits.modules = modules;
    traits.min_log_level = 3;
    traits.log_dir = "";

    g_rime->setup(&traits);
    g_rime->initialize(&traits);
    if (g_rime->start_maintenance(True)) {
        g_rime->join_maintenance_thread();
    }
    g_session = g_rime->create_session();
    g_initialized = g_session != 0;
    return g_initialized ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_openkeyboard_ioskeyboard_RimeNativeBridge_nativeSelectSchema(
        JNIEnv* env, jclass, jstring schema_id) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!ensure_session()) {
        return snapshot_array(env);
    }
    std::string schema = to_string(env, schema_id);
    if (!schema.empty()) {
        g_rime->select_schema(g_session, schema.c_str());
        g_rime->clear_composition(g_session);
    }
    return snapshot_array(env);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_openkeyboard_ioskeyboard_RimeNativeBridge_nativeProcessKey(
        JNIEnv* env, jclass, jint key_code) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!ensure_session()) {
        return snapshot_array(env);
    }
    g_rime->process_key(g_session, static_cast<int>(key_code), 0);
    return snapshot_array(env, take_commit());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_openkeyboard_ioskeyboard_RimeNativeBridge_nativeSelectCandidate(
        JNIEnv* env, jclass, jint index) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!ensure_session()) {
        return snapshot_array(env);
    }
    if (index >= 0) {
        g_rime->select_candidate(g_session, static_cast<size_t>(index));
    }
    return snapshot_array(env, take_commit());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_openkeyboard_ioskeyboard_RimeNativeBridge_nativeCommitComposition(
        JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!ensure_session()) {
        return snapshot_array(env);
    }
    g_rime->commit_composition(g_session);
    return snapshot_array(env, take_commit());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_openkeyboard_ioskeyboard_RimeNativeBridge_nativeClear(
        JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_rime && g_session) {
        g_rime->clear_composition(g_session);
    }
    return snapshot_array(env);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_openkeyboard_ioskeyboard_RimeNativeBridge_nativeSnapshot(
        JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return snapshot_array(env);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_openkeyboard_ioskeyboard_RimeNativeBridge_nativeFinalize(
        JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_rime) {
        if (g_session) {
            g_rime->destroy_session(g_session);
            g_session = 0;
        }
        g_rime->finalize();
    }
    g_rime = nullptr;
    g_initialized = false;
}
