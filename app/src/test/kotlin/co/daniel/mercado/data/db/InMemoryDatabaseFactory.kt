package co.daniel.mercado.data.db

import android.content.Context
import androidx.room.Room

/** Solo pruebas JVM (Robolectric): Room en memoria sin cifrar, con el mismo callback. No va en la app. */
class InMemoryDatabaseFactory : DatabaseFactory {
    override fun create(context: Context): MercadoDatabase =
        Room.inMemoryDatabaseBuilder(context, MercadoDatabase::class.java)
            .addCallback(MercadoDatabaseCallback)
            .build()
}
