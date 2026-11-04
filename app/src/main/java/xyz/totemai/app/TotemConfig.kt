package xyz.totemai.app

object TotemConfig {
    const val ORIGIN = "https://beta.totemai.xyz"
    const val HOME_URL = "$ORIGIN/"
    const val LOGIN_URL = "$ORIGIN/login"
    const val WORKSPACE_URL = "$ORIGIN/"
    const val WALLET_URL = "$ORIGIN/settings/mobile-connections"

    const val KEY_TOKEN = "anythingllm_authToken"
    const val KEY_USER = "anythingllm_user"
    const val BRIDGE_NAME = "TotemNative"

    const val APP_VERSION = "2.0"
    const val UA_SUFFIX = " TotemAI/$APP_VERSION"
    const val DEEP_LINK_SCHEME = "totemai"

    const val SITE_URL = "https://totemai.xyz"
    const val REPO_URL = "https://github.com/fred-terzi/totem-llm"
}
