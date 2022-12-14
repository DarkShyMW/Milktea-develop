package net.pantasystem.milktea.data.infrastructure.gallery

import net.pantasystem.milktea.data.api.misskey.MisskeyAPIProvider
import net.pantasystem.milktea.api.misskey.v12_75_0.GetPosts
import net.pantasystem.milktea.api.misskey.v12_75_0.LikedGalleryPost
import net.pantasystem.milktea.api.misskey.v12_75_0.MisskeyAPIV1275
import net.pantasystem.milktea.model.instance.IllegalVersionException

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import net.pantasystem.milktea.common.*
import net.pantasystem.milktea.common.paginator.*
import net.pantasystem.milktea.data.infrastructure.*
import net.pantasystem.milktea.model.account.Account
import net.pantasystem.milktea.model.drive.FilePropertyDataSource
import net.pantasystem.milktea.model.gallery.GalleryDataSource
import net.pantasystem.milktea.model.gallery.GalleryPost
import net.pantasystem.milktea.model.user.UserDataSource

data class LikedGalleryPostId(
    val id: String,
    val postId: GalleryPost.Id
)

class LikedGalleryPostsState : PaginationState<LikedGalleryPostId>, IdGetter<String>, GetGalleryPostsStateFlow {

    private val _state = MutableStateFlow<PageableState<List<LikedGalleryPostId>>>(PageableState.Fixed(
        StateContent.NotExist()))

    override fun getFlow(): Flow<PageableState<List<GalleryPost.Id>>> {
        return _state.map {
            it.convert { list ->
                list.map { liked ->
                    liked.postId
                }
            }

        }
    }


    private fun getList(): List<LikedGalleryPostId> {
        return (_state.value.content as? StateContent.Exist)?.rawContent?: emptyList()
    }
    override suspend fun getSinceId(): String? {
        return getList().firstOrNull()?.id
    }

    override suspend fun getUntilId(): String? {
        return getList().lastOrNull()?.id
    }


    override val state: Flow<PageableState<List<LikedGalleryPostId>>> = _state

    override fun getState(): PageableState<List<LikedGalleryPostId>> {
        return _state.value
    }

    override fun setState(state: PageableState<List<LikedGalleryPostId>>) {
        this._state.value = state
    }
}

class LikedGalleryPostsConverter(
    private val getAccount: suspend () -> Account,
    private val filePropertyDataSource: FilePropertyDataSource,
    private val userDataSource: UserDataSource,
    private val galleryDataSource: GalleryDataSource
) : EntityConverter<LikedGalleryPost, LikedGalleryPostId> {

    override suspend fun convertAll(list: List<LikedGalleryPost>): List<LikedGalleryPostId> {
        val account = getAccount.invoke()
        return list.map {
            LikedGalleryPostId(
                it.id,
                it.post.toEntity(account, filePropertyDataSource, userDataSource).also { post ->
                    galleryDataSource.add(post)
                }.id
            )
        }
    }
}

class LikedGalleryPostsLoader(
    private val idGetter: IdGetter<String>,
    private val misskeyAPIProvider: MisskeyAPIProvider,
    private val getAccount: suspend ()-> Account,
    private val encryption: Encryption
) : FutureLoader<LikedGalleryPost>, PreviousLoader<LikedGalleryPost> {

    override suspend fun loadFuture(): Result<List<LikedGalleryPost>> {
        return runCatching {
            val api = misskeyAPIProvider.get(getAccount.invoke().instanceDomain) as? MisskeyAPIV1275
                ?: throw IllegalVersionException()
            api.likedGalleryPosts(
                GetPosts(
                    sinceId = idGetter.getSinceId(),
                    i = getAccount.invoke().getI(encryption)
                )
            ).throwIfHasError().body()!!
        }

    }

    override suspend fun loadPrevious(): Result<List<LikedGalleryPost>> {
        return runCatching {
            val api = misskeyAPIProvider.get(getAccount.invoke().instanceDomain) as? MisskeyAPIV1275
                ?: throw IllegalVersionException()
            api.likedGalleryPosts(
                GetPosts(
                    untilId = idGetter.getUntilId(),
                    i = getAccount.invoke().getI(encryption)
                )
            ).throwIfHasError().body()!!
        }

    }
}