package com.juhao.murexide.repository

import com.juhao.murexide.data.GroupMember
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.group.list_member
import com.juhao.murexide.proto.group.list_member_send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 群成员相关操作
 */
class GroupMemberRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL

    suspend fun listMembers(
        token: String,
        groupId: String,
        page: Int = 1,
        size: Int = 50,
        keywords: String = ""
    ): Result<List<GroupMember>> = withContext(Dispatchers.IO) {
        try {
            val body = list_member_send(
                data_ = list_member_send.Data(size = size, page = page),
                group_id = groupId,
                keywords = keywords
            ).encode()

            val request = Request.Builder()
                .url("$baseUrl/v1/group/list-member")
                .post(body.toRequestBody("application/octet-stream".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val result = list_member.ADAPTER.decode(response.body.bytes())
                if (result.status?.code != 1) {
                    return@use Result.failure(Exception(result.status?.msg ?: "加载失败"))
                }
                val members = result.user.mapNotNull { u ->
                    val info = u.user_info ?: return@mapNotNull null
                    GroupMember(
                        userId = info.user_id,
                        name = info.name,
                        avatarUrl = info.avatar_url,
                        permissionLevel = u.permission_level,
                        isVip = info.is_vip == 1,
                        isGag = u.is_gag == 1,
                        gagTime = u.gag_time
                    )
                }
                Result.success(members)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class GroupRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL


    suspend fun removeMember(
        token: String,
        groupId: String,
        userId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("groupId", groupId)
                put("userId", userId)
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/group/remove-member")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val resp = JSONObject(response.body.string())
                if (resp.optInt("code") != 1) {
                    return@use Result.failure(Exception(resp.optString("msg", "操作失败")))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun gagMember(
        token: String,
        groupId: String,
        userId: String,
        gag: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("groupId", groupId)
                put("userId", userId)
                put("gag", gag)
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/group/gag-member")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val resp = JSONObject(response.body.string())
                if (resp.optInt("code") != 1) {
                    return@use Result.failure(Exception(resp.optString("msg", "操作失败")))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setAdmin(
        token: String,
        groupId: String,
        userId: String,
        isAdmin: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("groupId", groupId)
                put("userId", userId)
                put("userLevel", if (isAdmin) 2 else 0)
            }

            val request = Request.Builder()
                .url("$baseUrl/v1/group/manage-setting")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .header("token", token)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP error: ${response.code}"))
                }
                val resp = JSONObject(response.body.string())
                if (resp.optInt("code") != 1) {
                    return@use Result.failure(Exception(resp.optString("msg", "操作失败")))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}