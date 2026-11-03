package com.nexa.social.data.sample

import com.nexa.social.domain.model.AccountOrigin
import com.nexa.social.domain.model.AccountStatus
import com.nexa.social.domain.model.Channel
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.CommunityCategory
import com.nexa.social.domain.model.CommunityEvent
import com.nexa.social.domain.model.CommunityPost
import com.nexa.social.domain.model.Conversation
import com.nexa.social.domain.model.Clip
import com.nexa.social.domain.model.EmailVerificationStatus
import com.nexa.social.domain.model.Message
import com.nexa.social.domain.model.NexaNotification
import com.nexa.social.domain.model.NotificationTargetKind
import com.nexa.social.domain.model.NotificationType
import com.nexa.social.domain.model.Post
import com.nexa.social.domain.model.ResourceKind
import com.nexa.social.domain.model.Space
import com.nexa.social.domain.model.SpaceCategory
import com.nexa.social.domain.model.SpaceChallenge
import com.nexa.social.domain.model.SpaceResource
import com.nexa.social.domain.model.Story
import com.nexa.social.domain.model.User
import com.nexa.social.domain.model.UserSummary
import java.util.concurrent.TimeUnit

/**
 * Bundled development content. Every entry is clearly marked as sample data:
 * these are NOT registered users, emails are not real and nothing is verified.
 */
object SampleData {

    private fun now(): Long = System.currentTimeMillis()
    private fun minutesAgo(m: Long): Long = now() - TimeUnit.MINUTES.toMillis(m)
    private fun hoursAgo(h: Long): Long = now() - TimeUnit.HOURS.toMillis(h)
    private fun daysAgo(d: Long): Long = now() - TimeUnit.DAYS.toMillis(d)

    val users: List<User> = listOf(
        User(
            id = "sample_ahmed",
            username = "ahmed",
            displayName = "أحمد محمد",
            email = null,
            emailVerification = EmailVerificationStatus.NOT_VERIFIED,
            avatarUri = null,
            coverUri = null,
            bio = "عاشق للتقنية وتصميم التجارب الرقمية.",
            createdAt = daysAgo(420),
            accountStatus = AccountStatus.ACTIVE,
            accountOrigin = AccountOrigin.SAMPLE,
            followersCount = 1240,
            followingCount = 310
        ),
        User(
            id = "sample_sara",
            username = "sara_dev",
            displayName = "سارة أحمد",
            email = null,
            emailVerification = EmailVerificationStatus.NOT_VERIFIED,
            avatarUri = null,
            coverUri = null,
            bio = "مطوّرة تطبيقات ومهتمة بالمجتمعات التفاعلية.",
            createdAt = daysAgo(365),
            accountStatus = AccountStatus.ACTIVE,
            accountOrigin = AccountOrigin.SAMPLE,
            followersCount = 2680,
            followingCount = 420
        ),
        User(
            id = "sample_mohammed",
            username = "m_otaibi",
            displayName = "محمد العتيبي",
            email = null,
            emailVerification = EmailVerificationStatus.NOT_VERIFIED,
            avatarUri = null,
            coverUri = null,
            bio = "منشئ محتوى تعليمي.",
            createdAt = daysAgo(300),
            accountStatus = AccountStatus.ACTIVE,
            accountOrigin = AccountOrigin.SAMPLE,
            followersCount = 5320,
            followingCount = 190
        ),
        User(
            id = "sample_layla",
            username = "layla.h",
            displayName = "ليلى حسن",
            email = null,
            emailVerification = EmailVerificationStatus.NOT_VERIFIED,
            avatarUri = null,
            coverUri = null,
            bio = "مصمّمة جرافيك.",
            createdAt = daysAgo(250),
            accountStatus = AccountStatus.ACTIVE,
            accountOrigin = AccountOrigin.SAMPLE,
            followersCount = 880,
            followingCount = 640
        ),
        User(
            id = "sample_khaled",
            username = "khaled_m",
            displayName = "خالد منصور",
            email = null,
            emailVerification = EmailVerificationStatus.NOT_VERIFIED,
            avatarUri = null,
            coverUri = null,
            bio = "مصور ومهتم بالعوالم المحلية.",
            createdAt = daysAgo(200),
            accountStatus = AccountStatus.ACTIVE,
            accountOrigin = AccountOrigin.SAMPLE,
            followersCount = 430,
            followingCount = 260
        ),
        User(
            id = "sample_fatima",
            username = "fatima",
            displayName = "فاطمة الهر",
            email = null,
            emailVerification = EmailVerificationStatus.NOT_VERIFIED,
            avatarUri = null,
            coverUri = null,
            bio = "كاتبة ومدربة أعمال.",
            createdAt = daysAgo(150),
            accountStatus = AccountStatus.ACTIVE,
            accountOrigin = AccountOrigin.SAMPLE,
            followersCount = 1560,
            followingCount = 300
        )
    )

    fun userById(id: String): User? = users.firstOrNull { it.id == id }

    private val ahmed = UserSummary.from(users[0])
    private val sara = UserSummary.from(users[1])
    private val mohammed = UserSummary.from(users[2])
    private val layla = UserSummary.from(users[3])
    private val khaled = UserSummary.from(users[4])
    private val fatima = UserSummary.from(users[5])

    val posts: List<Post> = listOf(
        Post(
            id = "sample_post_1",
            author = mohammed,
            text = "بدأنا اليوم بتحدي تصميم واجهة لتطبيق مجتمعي. شاركونا أفكاركم في مجتمع Creative World.",
            imageUri = null,
            audience = com.nexa.social.domain.model.Audience.PUBLIC_LOCAL,
            createdAt = hoursAgo(5),
            likeCount = 1200,
            commentCount = 256,
            likedByMe = false,
            savedByMe = false,
            communityId = null,
            isLocal = true
        ),
        Post(
            id = "sample_post_2",
            author = sara,
            text = "أفضل ممارسة لتقسيم الكود في Compose: افصل الحالة عن الواجهة، واستخدم طبقة بيانات واضحة.",
            imageUri = null,
            audience = com.nexa.social.domain.model.Audience.PUBLIC_LOCAL,
            createdAt = hoursAgo(9),
            likeCount = 640,
            commentCount = 98,
            likedByMe = true,
            savedByMe = false,
            communityId = null,
            isLocal = true
        ),
        Post(
            id = "sample_post_3",
            author = ahmed,
            text = "مساء الخير من عالم الألعاب! جاهزون لفعالية نهاية الأسبوع؟",
            imageUri = null,
            audience = com.nexa.social.domain.model.Audience.PUBLIC_LOCAL,
            createdAt = daysAgo(1),
            likeCount = 3200,
            commentCount = 512,
            likedByMe = false,
            savedByMe = true,
            communityId = null,
            isLocal = true
        ),
        Post(
            id = "sample_post_4",
            author = layla,
            text = "أنهيت اليوم أول لوحة رقمية بعنوان «مدن الغد». رأيكم؟",
            imageUri = null,
            audience = com.nexa.social.domain.model.Audience.PUBLIC_LOCAL,
            createdAt = daysAgo(2),
            likeCount = 980,
            commentCount = 140,
            likedByMe = false,
            savedByMe = false,
            communityId = null,
            isLocal = true
        ),
        Post(
            id = "sample_post_5",
            author = fatima,
            text = "نصيحة لأصحاب المشاريع: ابدأ صغيرًا واختبر الفكرة قبل التوسع.",
            imageUri = null,
            audience = com.nexa.social.domain.model.Audience.PUBLIC_LOCAL,
            createdAt = daysAgo(3),
            likeCount = 520,
            commentCount = 61,
            likedByMe = false,
            savedByMe = false,
            communityId = null,
            isLocal = true
        )
    )

    val communities: List<Community> = listOf(
        Community(
            id = "world_gaming",
            name = "Gaming World",
            description = "عالم للألعاب والنقاشات والتحديات وبطولات المجتمع.",
            coverColorSeed = 0xFF7C4DFF,
            coverUri = null,
            category = CommunityCategory.GAMING,
            sampleMemberCount = 32400,
            isJoinedLocally = true,
            isNew = false,
            channels = listOf(
                Channel("g_news", "أخبار الألعاب", "آخر أخبار الإصدارات"),
                Channel("g_lfg", "ابحث عن فريق", "تجمّع للعب الجماعي")
            ),
            rules = listOf("احترم جميع الأعضاء", "لا للتنمر أو الإساءة", "التزم بمواضيع القناة"),
            events = listOf(
                CommunityEvent("g_e1", "بطولة نهاية الأسبوع", now() + TimeUnit.DAYS.toMillis(3), "أونلاين"),
                CommunityEvent("g_e2", "جلسة نقاش: مستقبل الألعاب", now() + TimeUnit.DAYS.toMillis(6), "قناة النقاش")
            )
        ),
        Community(
            id = "world_tech",
            name = "Tech World",
            description = "تقنية وبرمجة وتطوير تطبيقات ومشاركة الخبرات.",
            coverColorSeed = 0xFF3D7BFF,
            coverUri = null,
            category = CommunityCategory.TECH,
            sampleMemberCount = 28700,
            isJoinedLocally = false,
            isNew = false,
            channels = listOf(Channel("t_android", "أندرويد", "تطوير تطبيقات أندرويد")),
            rules = listOf("شارك المعرفة ببساطة", "اذكر المصدر عند الاقتباس"),
            events = listOf(CommunityEvent("t_e1", "ورشة Compose", now() + TimeUnit.DAYS.toMillis(4), "أونلاين"))
        ),
        Community(
            id = "world_education",
            name = "Education World",
            description = "تعلّم وشارك المعرفة والدروس والمراجع التعليمية.",
            coverColorSeed = 0xFF00D0E0,
            coverUri = null,
            category = CommunityCategory.EDUCATION,
            sampleMemberCount = 18300,
            isJoinedLocally = false,
            isNew = false,
            channels = listOf(Channel("e_math", "الرياضيات", "دروس وتمارين")),
            rules = listOf("كن إيجابيًا", "لا تشارك محتوى منسوخًا"),
            events = emptyList()
        ),
        Community(
            id = "world_creative",
            name = "Creative World",
            description = "تصميم وفنون وإبداع بصري ولوحات رقمية.",
            coverColorSeed = 0xFFB388FF,
            coverUri = null,
            category = CommunityCategory.CREATIVE,
            sampleMemberCount = 15600,
            isJoinedLocally = true,
            isNew = true,
            channels = listOf(Channel("c_design", "التصميم", "أعمال ونقد بنّاء")),
            rules = listOf("احترم حقوق الأعمال", "قدّم ملاحظات بناءة"),
            events = listOf(CommunityEvent("c_e1", "تحدي التصميم الأسبوعي", now() + TimeUnit.DAYS.toMillis(2), "أونلاين"))
        ),
        Community(
            id = "world_business",
            name = "Business World",
            description = "ريادة أعمال ومشاريع وتسويق وتبادل الفرص.",
            coverColorSeed = 0xFF27C08A,
            coverUri = null,
            category = CommunityCategory.BUSINESS,
            sampleMemberCount = 12400,
            isJoinedLocally = false,
            isNew = false,
            channels = emptyList(),
            rules = listOf("لا للإعلانات العشوائية", "التزم بالشفافية"),
            events = emptyList()
        ),
        Community(
            id = "world_local",
            name = "Local World",
            description = "مجتمع محلي للفعاليات والأخبار وتجارب الحي.",
            coverColorSeed = 0xFFFFA53D,
            coverUri = null,
            category = CommunityCategory.LOCAL,
            sampleMemberCount = 9800,
            isJoinedLocally = false,
            isNew = true,
            channels = listOf(Channel("l_events", "الفعاليات", "كل ما يخص الفعاليات المحلية")),
            rules = listOf("احترم الخصوصية", "لا تنشر معلومات شخصية"),
            events = listOf(CommunityEvent("l_e1", "لقاء الجيران", now() + TimeUnit.DAYS.toMillis(5), "الحديقة العامة"))
        )
    )

    fun communityById(id: String): Community? = communities.firstOrNull { it.id == id }

    val communityPosts: List<CommunityPost> = listOf(
        CommunityPost(
            id = "cp_g1",
            communityId = "world_gaming",
            author = ahmed,
            text = "من يريد الانضمام لفريق البطولة؟ نحتاج لاعبين اثنين.",
            imageUri = null,
            createdAt = hoursAgo(3),
            likeCount = 210,
            commentCount = 42,
            likedByMe = false,
            isLocal = true
        ),
        CommunityPost(
            id = "cp_g2",
            communityId = "world_gaming",
            author = khaled,
            text = "ملخص بطولة الأسبوع الماضي، كانت مباريات قوية!",
            imageUri = null,
            createdAt = daysAgo(1),
            likeCount = 530,
            commentCount = 88,
            likedByMe = false,
            isLocal = true
        ),
        CommunityPost(
            id = "cp_c1",
            communityId = "world_creative",
            author = layla,
            text = "شاركوا أعمالكم في تحدّي هذا الأسبوع: «مدن الغد».",
            imageUri = null,
            createdAt = hoursAgo(7),
            likeCount = 340,
            commentCount = 51,
            likedByMe = false,
            isLocal = true
        ),
        CommunityPost(
            id = "cp_t1",
            communityId = "world_tech",
            author = sara,
            text = "أفضل ممارسة: استخدم StateFlow بدل المتغيرات العامة.",
            imageUri = null,
            createdAt = hoursAgo(11),
            likeCount = 410,
            commentCount = 37,
            likedByMe = false,
            isLocal = true
        )
    )

    val conversations: List<Conversation> = listOf(
        Conversation(
            id = "conv_sara",
            participant = sara,
            lastMessagePreview = "تمام، نتابع التصميم غدًا.",
            lastTimestamp = minutesAgo(12),
            unreadCount = 2,
            isLocalSample = true,
            messages = listOf(
                Message("m1", "conv_sara", sara.id, "مرحبًا! هل اطلعت على التصميم الجديد؟", minutesAgo(40), isRead = true, isMine = false, isLocal = true),
                Message("m2", "conv_sara", "me", "نعم، أعجبني اتجاه الألوان.", minutesAgo(30), isRead = true, isMine = true, isLocal = true),
                Message("m3", "conv_sara", sara.id, "تمام، نتابع التصميم غدًا.", minutesAgo(12), isRead = false, isMine = false, isLocal = true)
            )
        ),
        Conversation(
            id = "conv_mohammed",
            participant = mohammed,
            lastMessagePreview = "شكرًا على المشاركة.",
            lastTimestamp = minutesAgo(45),
            unreadCount = 0,
            isLocalSample = true,
            messages = listOf(
                Message("m4", "conv_mohammed", "me", "شاركت في التحدي، شكرًا!", minutesAgo(60), isRead = true, isMine = true, isLocal = true),
                Message("m5", "conv_mohammed", mohammed.id, "شكرًا على المشاركة.", minutesAgo(45), isRead = true, isMine = false, isLocal = true)
            )
        ),
        Conversation(
            id = "conv_layla",
            participant = layla,
            lastMessagePreview = "بالتوفيق في العمل الجديد!",
            lastTimestamp = hoursAgo(5),
            unreadCount = 0,
            isLocalSample = true,
            messages = listOf(
                Message("m6", "conv_layla", layla.id, "بالتوفيق في العمل الجديد!", hoursAgo(5), isRead = true, isMine = false, isLocal = true)
            )
        ),
        Conversation(
            id = "conv_khaled",
            participant = khaled,
            lastMessagePreview = "أرسل لي صور الرحلة لاحقًا.",
            lastTimestamp = daysAgo(1),
            unreadCount = 1,
            isLocalSample = true,
            messages = listOf(
                Message("m7", "conv_khaled", khaled.id, "أرسل لي صور الرحلة لاحقًا.", daysAgo(1), isRead = false, isMine = false, isLocal = true)
            )
        )
    )

    val notifications: List<NexaNotification> = listOf(
        NexaNotification(
            id = "n1",
            type = NotificationType.LIKE,
            title = "أعجب محمد العتيبي بمنشورك",
            body = "قبل 10 دقائق",
            timestamp = minutesAgo(10),
            isRead = false,
            actorName = mohammed.displayName,
            actorAvatarUri = null
        ),
        NexaNotification(
            id = "n2",
            type = NotificationType.COMMENT,
            title = "علّقت سارة أحمد على منشورك",
            body = "قبل ساعة",
            timestamp = hoursAgo(1),
            isRead = false,
            actorName = sara.displayName,
            actorAvatarUri = null
        ),
        NexaNotification(
            id = "n3",
            type = NotificationType.FOLLOW,
            title = "بدأ خالد منصور بمتابعتك",
            body = "قبل 3 ساعات",
            timestamp = hoursAgo(3),
            isRead = true,
            actorName = khaled.displayName,
            actorAvatarUri = null
        ),
        NexaNotification(
            id = "n4",
            type = NotificationType.COMMUNITY,
            title = "انضممت إلى Creative World",
            body = "مرحبًا بك في العالم الإبداعي",
            timestamp = daysAgo(1),
            isRead = true,
            actorName = null,
            actorAvatarUri = null
        ),
        NexaNotification(
            id = "n5",
            type = NotificationType.WELCOME,
            title = "مرحبًا بك في Nexa 2030",
            body = "هذا إصدار محلي. بعض الميزات تنتظر ربط الخادم.",
            timestamp = daysAgo(2),
            isRead = true,
            actorName = null,
            actorAvatarUri = null,
            targetKind = NotificationTargetKind.NONE,
            targetId = null
        )
    )

    /** Sample stories. Expire soon so the "expired" behaviour is observable locally. */
    fun stories(): List<Story> = listOf(
        Story(
            id = "story_1",
            author = sara,
            imageUri = null,
            text = "جلسة تصميم صباحية ☕",
            createdAt = hoursAgo(2),
            expiresAt = now() + hoursAgo(-22),
            viewedLocally = false,
            isMine = false
        ),
        Story(
            id = "story_2",
            author = mohammed,
            imageUri = null,
            text = "بداية تحدي التصميم الأسبوعي",
            createdAt = hoursAgo(4),
            expiresAt = now() + hoursAgo(-20),
            viewedLocally = false,
            isMine = false
        ),
        Story(
            id = "story_3",
            author = layla,
            imageUri = null,
            text = "لوحة جديدة قيد العمل 🎨",
            createdAt = hoursAgo(9),
            expiresAt = now() + hoursAgo(-15),
            viewedLocally = true,
            isMine = false
        )
    )

    /** Sample spaces. Resources are local references, not uploaded files. */
    fun spaces(): List<Space> = listOf(
        Space(
            id = "space_compose",
            communityId = "world_tech",
            title = "أساسيات Compose",
            description = "مسار تعليمي مبسّط لبناء واجهات Jetpack Compose.",
            category = SpaceCategory.PROGRAMMING,
            creatorId = sara.id,
            creatorName = sara.displayName,
            createdAt = daysAgo(20),
            resources = listOf(
                SpaceResource("sr1", "مقدمة في الحالة", "شرح إدارة الحالة في Compose.", ResourceKind.NOTE, null),
                SpaceResource("sr2", "مرجع رسمي", "صفحة التوثيق الرسمية لـ Compose.", ResourceKind.LINK, "https://developer.android.com/jetpack/compose")
            ),
            challenges = listOf(
                SpaceChallenge("sc1", "أنشئ بطاقة منشور بسيطة"),
                SpaceChallenge("sc2", "ارفع حالة إلى ViewModel")
            ),
            discussionCount = 12
        ),
        Space(
            id = "space_design",
            communityId = "world_creative",
            title = "أساسيات التصميم البصري",
            description = "مبادئ التكوين واللون والتباعد في الواجهات.",
            category = SpaceCategory.DESIGN,
            creatorId = layla.id,
            creatorName = layla.displayName,
            createdAt = daysAgo(12),
            resources = listOf(
                SpaceResource("sr3", "نظرية اللون", "ملاحظات حول تناسق الألوان.", ResourceKind.NOTE, null)
            ),
            challenges = listOf(SpaceChallenge("sc3", "صمّم تدرّجًا لونيًا لواجهتك")),
            discussionCount = 7
        ),
        Space(
            id = "space_math",
            communityId = "world_education",
            title = "الرياضيات التفاعلية",
            description = "تمارين ونقاشات في الرياضيات بمستويات متدرجة.",
            category = SpaceCategory.EDUCATION,
            creatorId = fatima.id,
            creatorName = fatima.displayName,
            createdAt = daysAgo(30),
            resources = emptyList(),
            challenges = listOf(SpaceChallenge("sc4", "حل تمرين الجبر رقم 4")),
            discussionCount = 3
        )
    )

    /**
     * Sample clips. No video file is bundled, so [Clip.videoUri] is null and the UI must state that
     * a local video has not been chosen yet instead of pretending to stream one.
     */
    fun clips(): List<Clip> = listOf(
        Clip(
            id = "clip_1",
            author = khaled,
            videoUri = null,
            caption = "لقطة سريعة من رحلة الأمس.",
            createdAt = hoursAgo(6),
            likeCount = 320,
            commentCount = 18,
            likedByMe = false,
            savedByMe = false,
            isLocal = true
        ),
        Clip(
            id = "clip_2",
            author = sara,
            videoUri = null,
            caption = "خطوة بخطوة: إعداد مشروع جديد.",
            createdAt = hoursAgo(14),
            likeCount = 540,
            commentCount = 42,
            likedByMe = false,
            savedByMe = false,
            isLocal = true
        )
    )
}
