package com.comai.contextengine.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [CommunityEvent::class],
    version = 1,
    exportSchema = false
)
abstract class CommunityEventDatabase : RoomDatabase() {

    abstract fun communityEventDao(): CommunityEventDao

    companion object {
        const val DATABASE_NAME = "community_events.db"
        const val ASSET_PATH = "databases/community_events.db"

        @Volatile
        private var INSTANCE: CommunityEventDatabase? = null

        fun getInstance(context: Context): CommunityEventDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    CommunityEventDatabase::class.java,
                    DATABASE_NAME
                )
                
                if (context.assets.list("databases")?.contains("community_events.db") == true) {
                    builder.createFromAsset(ASSET_PATH)
                } else {
                    builder.addCallback(PrepopulateCallback())
                }

                val instance = builder.fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }

        private class PrepopulateCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.communityEventDao()?.insertEvents(MOCK_COMMUNITY_EVENTS)
                }
            }
        }

        val MOCK_COMMUNITY_EVENTS = listOf(
            CommunityEvent(
                eventName = "Sunset Outdoor Calisthenics Circle",
                category = "FITNESS",
                locationArea = "Home Area",
                applicableOnWorkDay = false,
                dayOfWeekApplicability = "WEEKEND",
                timeWindowStart = "17:30",
                timeWindowEnd = "19:00",
                description = "Casual outdoor workout group at Central Park pullup bars."
            ),
            CommunityEvent(
                eventName = "Weekend Board Games & Specialty Coffee",
                category = "COMMUNITY",
                locationArea = "Downtown",
                applicableOnWorkDay = false,
                dayOfWeekApplicability = "SATURDAY",
                timeWindowStart = "18:00",
                timeWindowEnd = "21:00",
                description = "Relaxed social gathering at Roasters Cafe for board games."
            ),
            CommunityEvent(
                eventName = "Night Running Tribe (5K Pace)",
                category = "FITNESS",
                locationArea = "Home Area",
                applicableOnWorkDay = false,
                dayOfWeekApplicability = "WEEKEND",
                timeWindowStart = "18:30",
                timeWindowEnd = "20:00",
                description = "Group 5k run along the riverfront trail, all paces welcome."
            ),
            CommunityEvent(
                eventName = "Local Artisan Farmers Market",
                category = "OUTDOORS",
                locationArea = "Home Area",
                applicableOnWorkDay = false,
                dayOfWeekApplicability = "WEEKEND",
                timeWindowStart = "09:00",
                timeWindowEnd = "14:00",
                description = "Fresh produce, local honeys, and handcrafted goods."
            ),
            CommunityEvent(
                eventName = "Indie Acoustic Live Jam",
                category = "COMMUNITY",
                locationArea = "Downtown",
                applicableOnWorkDay = false,
                dayOfWeekApplicability = "WEEKEND",
                timeWindowStart = "19:00",
                timeWindowEnd = "22:00",
                description = "Acoustic open-mic session at The Underground Velvet Lounge."
            ),
            CommunityEvent(
                eventName = "Tech Founders & Builders Meetup",
                category = "NETWORKING",
                locationArea = "Tech Hub",
                applicableOnWorkDay = true,
                dayOfWeekApplicability = "THURSDAY",
                timeWindowStart = "18:00",
                timeWindowEnd = "20:30",
                description = "Informal networking drink & talk for local indie developers."
            ),
            CommunityEvent(
                eventName = "Mindful Yoga & Breathwork",
                category = "FITNESS",
                locationArea = "Home Area",
                applicableOnWorkDay = false,
                dayOfWeekApplicability = "SUNDAY",
                timeWindowStart = "17:00",
                timeWindowEnd = "18:15",
                description = "Guided sunset stretch and mindfulness session."
            ),
            CommunityEvent(
                eventName = "Street Food & Taco Crawl",
                category = "FOOD",
                locationArea = "Downtown",
                applicableOnWorkDay = false,
                dayOfWeekApplicability = "WEEKEND",
                timeWindowStart = "18:00",
                timeWindowEnd = "21:30",
                description = "Exploring local food truck pop-ups in the Arts District."
            )
        )
    }
}
