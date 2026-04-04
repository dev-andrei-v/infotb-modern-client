package ro.infotb.modern.data.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.infotb.modern.data.db.FavoriteDao
import ro.infotb.modern.data.db.FavoriteEntity

class FavoritesStore(
    private val dao: FavoriteDao,
    private val scope: CoroutineScope
) {

    val favoriteLineIds: StateFlow<List<Long>> = dao.observeAllIds()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun toggleFavorite(lineId: Long) {
        scope.launch {
            if (dao.isFavorite(lineId)) {
                dao.delete(lineId)
            } else {
                dao.insert(FavoriteEntity(lineId))
            }
        }
    }

    fun isFavorite(lineId: Long): Boolean {
        return favoriteLineIds.value.contains(lineId)
    }
}
