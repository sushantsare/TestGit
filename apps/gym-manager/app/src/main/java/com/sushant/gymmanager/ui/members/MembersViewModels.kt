package com.sushant.gymmanager.ui.members

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sushant.gymmanager.GymApp
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.data.repository.MemberWithPlan
import com.sushant.gymmanager.data.repository.PlanInput
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.PhotoStore
import com.sushant.gymmanager.util.WhatsAppHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import com.sushant.gymmanager.ui.components.matches
import com.sushant.gymmanager.ui.navigation.NavRequests
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MemberFilter {
    const val ALL = "ALL"
    const val EXPIRING = "EXPIRING"
    const val EXPIRED = "EXPIRED"
    const val DUES = "DUES"
    const val FROZEN = "FROZEN"
    const val MORNING = "MORNING"
    const val EVENING = "EVENING"
    val all = listOf(ALL, EXPIRING, EXPIRED, DUES, FROZEN, MORNING, EVENING)
}

object MemberSort {
    const val ROLL = "ROLL"
    const val NAME = "NAME"
    const val EXPIRY = "EXPIRY"
    const val JOINED = "JOINED"
    val all = listOf(ROLL, NAME, EXPIRY, JOINED)
}

class MembersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as GymApp).repository

    val query = MutableStateFlow("")
    val inactiveQuery = MutableStateFlow("")
    val filter = MutableStateFlow(MemberFilter.ALL)
    val sort = MutableStateFlow(MemberSort.ROLL)

    init {
        // A dashboard tile can ask for a filter ("Expiring soon").
        viewModelScope.launch {
            NavRequests.memberFilter.collect { f ->
                if (f != null) {
                    filter.value = f
                    NavRequests.memberFilter.value = null
                }
            }
        }
    }

    val settings: StateFlow<GymSettingsEntity> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GymSettingsEntity())

    val allActive: StateFlow<List<MemberWithPlan>?> =
        repo.activeMembersWithPlans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Member ids that owe money. */
    private val owing = repo.unpaidCycles.map { list -> list.filter { it.balance > 0.0 }.map { it.memberId }.toSet() }

    private val options = combine(query, filter, sort) { q, f, so -> Triple(q, f, so) }

    val filtered: StateFlow<List<MemberWithPlan>?> = combine(repo.activeMembersWithPlans, options, owing, repo.settings) { list, opt, dues, st ->
        val (q, f, so) = opt
        val days = st.expiringSoonDays
        list.filter { (m, plan) ->
            m.matches(q) && when (f) {
                MemberFilter.EXPIRING -> m.frozenFrom == null && plan != null && DateUtils.isExpiringSoon(plan.expiryDate, days)
                MemberFilter.EXPIRED -> m.frozenFrom == null && plan != null && DateUtils.isExpired(plan.expiryDate)
                MemberFilter.DUES -> m.id in dues
                MemberFilter.FROZEN -> m.frozenFrom != null
                MemberFilter.MORNING -> m.shift == com.sushant.gymmanager.data.local.Shift.MORNING
                MemberFilter.EVENING -> m.shift == com.sushant.gymmanager.data.local.Shift.EVENING
                else -> true
            }
        }.let { l ->
            when (so) {
                MemberSort.NAME -> l.sortedBy { it.member.fullName.lowercase() }
                MemberSort.EXPIRY -> l.sortedBy { it.membership?.expiryDate ?: "9999" }
                MemberSort.JOINED -> l.sortedByDescending { it.member.dateOfJoining }
                else -> l
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val inactive: StateFlow<List<MemberWithPlan>?> = combine(repo.inactiveMembersWithPlans, inactiveQuery) { list, q ->
        list.filter { it.member.matches(q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val inactiveCount: StateFlow<Int> = repo.inactiveMembers.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}

@kotlinx.serialization.Serializable
data class MemberForm(
    val photoPath: String? = null,
    val rollNumber: String = "",
    val fullName: String = "",
    val whatsapp: String = "",
    val dateOfBirth: String = "",
    val dateOfJoining: String = DateUtils.today(),
    val fatherName: String = "",
    val motherName: String = "",
    val fromTime: String = "",
    val toTime: String = "",
    val planType: String = PlanType.ONE_MONTH,
    val customMonths: String = "1",
    val customDays: String = "0",
    val fee: String = "",
    val autoRenew: Boolean = true
)

class AddEditMemberViewModel(app: Application, private val handle: SavedStateHandle) : AndroidViewModel(app) {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val gymApp = app as GymApp
    private val repo = gymApp.repository

    var memberId: Long = 0L
        private set
    var form by mutableStateOf(MemberForm())
        private set
    var original by mutableStateOf(MemberForm())
        private set
    var loading by mutableStateOf(true)
        private set
    var rollTaken by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var settings by mutableStateOf(GymSettingsEntity())
        private set
    var photoImporting by mutableStateOf(false)
        private set
    /** Fields the user has left at least once; their errors are shown from then on. */
    var touched by mutableStateOf(handle.get<Array<String>>("touched")?.toSet() ?: emptySet())
        private set
    /** Set after a Save attempt with errors, so every remaining error shows. */
    var saveAttempted by mutableStateOf(handle.get<Boolean>("saveAttempted") ?: false)
        private set

    fun touch(key: String) {
        if (key !in touched) {
            touched = touched + key
            handle["touched"] = touched.toTypedArray()
        }
    }

    fun markSaveAttempted() {
        saveAttempted = true
        handle["saveAttempted"] = true
    }

    fun showError(key: String): Boolean = (key in touched || saveAttempted) && errors.containsKey(key)

    private fun persistForm() {
        handle["form"] = json.encodeToString(MemberForm.serializer(), form)
        handle["original"] = json.encodeToString(MemberForm.serializer(), original)
    }

    private fun restoredForm(key: String): MemberForm? =
        handle.get<String>(key)?.let { runCatching { json.decodeFromString(MemberForm.serializer(), it) }.getOrNull() }

    private var existing: MemberEntity? = null
    private var currentExpiry: String? = null
    private var periodAnchor: String? = null
    private val sessionPhotos = mutableListOf<String>()
    private var savedPhoto: String? = null
    private var rollJob: Job? = null
    private var initialized = false

    fun init(id: Long) {
        if (initialized) return
        initialized = true
        memberId = id
        viewModelScope.launch {
            settings = repo.getSettings()
            val savedForm = restoredForm("form")
            val savedOriginal = restoredForm("original")
            if (id > 0) {
                val m = repo.getMember(id)
                val plan = repo.getActiveMembership(id)
                existing = m
                currentExpiry = plan?.expiryDate
                periodAnchor = repo.latestCycle(id)?.cycleStart ?: plan?.startDate
                if (m != null) {
                    val f = MemberForm(
                        photoPath = m.photoUri,
                        rollNumber = m.rollNumber,
                        fullName = m.fullName,
                        whatsapp = m.whatsappNumber,
                        dateOfBirth = m.dateOfBirth,
                        dateOfJoining = m.dateOfJoining,
                        fatherName = m.fatherName.orEmpty(),
                        motherName = m.motherName.orEmpty(),
                        fromTime = m.fromTime,
                        toTime = m.toTime,
                        planType = plan?.planType ?: PlanType.ONE_MONTH,
                        customMonths = (plan?.durationMonths ?: 1).toString(),
                        customDays = (plan?.durationDays ?: 0).toString(),
                        fee = plan?.fee?.let { formatFee(it) } ?: formatFee(settings.fee1Month),
                        autoRenew = plan?.autoRenew ?: true
                    )
                    form = f; original = f
                }
            } else {
                val f = MemberForm(fee = formatFee(settings.fee1Month))
                form = f; original = f
            }
            // Coming back after Android closed the app in the background: restore what was typed.
            if (savedForm != null && savedOriginal != null) {
                form = savedForm; original = savedOriginal
                savedForm.photoPath?.let { if (it != savedOriginal.photoPath) sessionPhotos += it }
            }
            persistForm()
            if (form.rollNumber.isNotBlank() && form.rollNumber != original.rollNumber) checkRoll(form.rollNumber)
            loading = false
        }
    }

    private fun formatFee(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun update(transform: (MemberForm) -> MemberForm) {
        val before = form
        form = transform(form)
        persistForm()
        if (form.rollNumber != before.rollNumber) checkRoll(form.rollNumber)
    }

    fun selectPlan(plan: String) {
        val def = settings.defaultFeeFor(plan)
        update {
            it.copy(
                planType = plan,
                fee = if (memberId == 0L && def != null) formatFee(def) else it.fee
            )
        }
    }

    private fun checkRoll(roll: String) {
        rollJob?.cancel()
        rollJob = viewModelScope.launch {
            delay(250)
            rollTaken = repo.isRollTaken(roll, memberId)
        }
    }

    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            photoImporting = true
            val path = withContext(Dispatchers.IO) { PhotoStore.importFromUri(getApplication(), uri) }
            photoImporting = false
            if (path != null) {
                sessionPhotos += path
                update { it.copy(photoPath = path) }
            }
        }
    }

    fun removePhoto() = update { it.copy(photoPath = null) }

    val shift: String
        get() = DateUtils.determineShift(form.fromTime, settings.morningShiftStart, settings.morningShiftEnd)

    private val planChanged: Boolean
        get() = memberId == 0L || form.planType != original.planType ||
            (form.planType == PlanType.CUSTOM && (form.customMonths != original.customMonths || form.customDays != original.customDays))

    val expiryPreview: String
        get() {
            val months = form.customMonths.toIntOrNull() ?: 0
            val days = form.customDays.toIntOrNull() ?: 0
            if (!planChanged && currentExpiry != null) return currentExpiry!!
            val start = if (memberId == 0L) form.dateOfJoining else (periodAnchor ?: form.dateOfJoining)
            return DateUtils.calculateExpiryDate(start, form.planType, months, days)
        }

    val isDirty: Boolean get() = form != original

    val errors: Map<String, String>
        get() = buildMap {
            if (form.rollNumber.isBlank()) put("roll", "Required")
            else if (rollTaken) put("roll", "Roll number already in use")
            if (form.fullName.isBlank()) put("name", "Required")
            val digits = form.whatsapp.filter { it.isDigit() }
            if (digits.isEmpty()) put("whatsapp", "Required")
            else if (digits.length < 10) put("whatsapp", "Enter a valid 10-digit number")
            if (form.fromTime.isBlank() || form.toTime.isBlank()) put("slot", "Select the workout time slot")
            else if (form.fromTime == form.toTime) put("slot", "Start and end time can't be the same")
            val fee = form.fee.toDoubleOrNull()
            if (fee == null || fee < 0) put("fee", "Enter a valid fee")
            if (form.planType == PlanType.CUSTOM) {
                val m = form.customMonths.toIntOrNull() ?: -1
                val d = form.customDays.toIntOrNull() ?: -1
                if (m < 0 || d < 0 || (m == 0 && d == 0)) put("custom", "Enter a duration of at least 1 day")
            }
        }

    val canSave: Boolean get() = !loading && !saving && errors.isEmpty()

    fun save(onSaved: () -> Unit) {
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            // Final uniqueness check straight from the database.
            if (repo.isRollTaken(form.rollNumber, memberId)) {
                rollTaken = true; saving = false; return@launch
            }
            val f = form
            val member = MemberEntity(
                id = memberId,
                rollNumber = f.rollNumber.trim(),
                fullName = f.fullName.trim(),
                photoUri = f.photoPath,
                dateOfBirth = f.dateOfBirth,
                whatsappNumber = f.whatsapp.trim(),
                dateOfJoining = f.dateOfJoining.ifBlank { DateUtils.today() },
                fatherName = f.fatherName.trim().ifBlank { null },
                motherName = f.motherName.trim().ifBlank { null },
                shift = shift,
                fromTime = f.fromTime,
                toTime = f.toTime,
                isActive = existing?.isActive ?: true,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            val plan = PlanInput(
                planType = f.planType,
                customMonths = f.customMonths.toIntOrNull() ?: 0,
                customDays = f.customDays.toIntOrNull() ?: 0,
                fee = f.fee.toDoubleOrNull() ?: 0.0,
                autoRenew = f.autoRenew
            )
            if (memberId == 0L) repo.addMember(member, plan) else repo.updateMember(member, plan)
            savedPhoto = f.photoPath
            original = form
            persistForm()
            saving = false
            onSaved()
        }
    }

    override fun onCleared() {
        // Remove photos picked in this session that were never saved.
        sessionPhotos.filter { it != savedPhoto }.forEach { PhotoStore.delete(it) }
        super.onCleared()
    }

    @Suppress("unused")
    private fun normalized(): String = WhatsAppHelper.normalizePhone(form.whatsapp)
}
