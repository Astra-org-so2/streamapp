package com.streamapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gamepad_layouts")
data class GamepadLayoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val leftStickX: Float = 0.15f,
    val leftStickY: Float = 0.70f,
    val rightStickX: Float = 0.85f,
    val rightStickY: Float = 0.70f,
    val dpadX: Float = 0.15f,
    val dpadY: Float = 0.40f,
    val abxyX: Float = 0.85f,
    val abxyY: Float = 0.40f,
    val buttonScale: Float = 1.0f,
    val opacity: Float = 0.65f,
    val hapticEnabled: Boolean = true
)
