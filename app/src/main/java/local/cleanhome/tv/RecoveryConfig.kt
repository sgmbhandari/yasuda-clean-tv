package local.cleanhome.tv

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException

object RecoveryConfig {
    fun read(context: Context): JSONObject {
        val file = File(context.filesDir, "recovery.json")
        if (!file.isFile || file.length() > 4096) throw IOException("Restore is not provisioned for this TV. Use the deployment tool to save its original setup and choose a reset PIN.")
        return JSONObject(file.readText()).also { spec ->
            if (!spec.optString("scriptSha256").matches(Regex("[a-f0-9]{64}")) ||
                !spec.optString("pinSalt").matches(Regex("[a-f0-9]{32}")) ||
                !spec.optString("pinHash").matches(Regex("[a-f0-9]{64}"))) throw IOException("Restore configuration is incomplete")
        }
    }
}
