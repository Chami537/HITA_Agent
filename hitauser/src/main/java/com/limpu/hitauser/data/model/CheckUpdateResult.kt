package com.limpu.hitauser.data.model

class CheckUpdateResult {
    var shouldUpdate: Boolean = false
    var latestVersionCode: Long = 0
    var latestVersionName: String = ""
    var latestUrl: String = ""
    var updateLog: String = ""
    var downloadUrl: String = ""
    var downloadCount: Long = 0
}
