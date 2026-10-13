package com.leanhome.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Exercise(
    val id: String,
    val nameEn: String,
    val nameVi: String,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
    val requiredEquipment: List<Equipment>,
    val contraindicatedJoints: List<Joint>,
    val difficultyLevel: DifficultyLevel,
    val videoUrl: String,
    val fallbackThumbnailUrl: String,
    val commonMistakes: List<String>,
    val correctFormCues: List<String>,
) {
    /**
     * Returns true if ALL of this exercise's required equipment is contained
     * in the user's available equipment set.
     */
    fun isAvailableWith(userEquipment: Set<Equipment>): Boolean =
        requiredEquipment.all { it in userEquipment }

    /**
     * Returns true if this exercise should be excluded given the user's
     * injury/joint flags. An exercise is excluded if ANY of its
     * contraindicated_joints overlap with the user's injury flags.
     */
    fun isContraindicatedFor(injuryFlags: Set<Joint>): Boolean =
        contraindicatedJoints.any { it in injuryFlags }
}
