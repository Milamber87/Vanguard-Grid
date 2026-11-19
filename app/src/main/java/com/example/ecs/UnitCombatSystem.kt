package com.example.ecs

import com.example.data.model.BuildingType
import com.example.data.model.UnitCatalog
import com.example.data.model.UnitFaction
import com.example.simulation.GRID_SIZE
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * ECS system driving Player (UEC Garrison) and Enemy (Xeno Hostile) units:
 * - Computes steering [Velocity] vectors consumed by [MovementSystem].
 * - Executes player patrol orbits, enemy target acquisition, ranged/melee combat,
 *   Valkyrie Repair Mech structural/unit welding, Rail-Turret target interception,
 *   and Wildlife Migration hostile wave spawns.
 */
class UnitCombatAndAiSystem : EcsSystem {
    override val name: String = "UnitCombatAndAiSystem"

    private var turretFireAccumulator = 0f
    private var raidSpawnAccumulator = 0f

    override fun update(world: EcsWorld, ctx: SimulationContext, dtSeconds: Float) {
        if (dtSeconds <= 0f) return
        val em = world.entityManager

        // 1. Decay transient CombatBeamComponent entities
        val expiredBeams = mutableListOf<EntityId>()
        for ((beamEntity, beam) in em.getEntitiesWith<CombatBeamComponent>()) {
            beam.ttlSeconds -= dtSeconds
            if (beam.ttlSeconds <= 0f) expiredBeams.add(beamEntity)
        }
        expiredBeams.forEach { em.removeEntity(it) }

        // 2. During Wildlife Raid dynamic events, spawn perimeter hostile units up to cap
        val activeUnitsSnapshot = em.getEntitiesWith3<Position, Velocity, UnitComponent>()
        val existingEnemyCount = activeUnitsSnapshot.count { it.c.faction == UnitFaction.ENEMY }
        if (ctx.activeEvent?.wildlifeRaidActive == true && existingEnemyCount < MAX_RAID_ENEMIES) {
            raidSpawnAccumulator += dtSeconds
            if (raidSpawnAccumulator >= 4.5f) {
                raidSpawnAccumulator -= 4.5f
                val wave = UnitCatalog.waveForBiome(ctx.planetId, waveTier = 1)
                val pick = wave[(ctx.tickCount.toInt() + existingEnemyCount) % wave.size]
                val edge = (ctx.tickCount.toInt() + existingEnemyCount) % 4
                val (sx, sy) = perimeterSpawnPoint(edge, ((ctx.tickCount * 3L) % (GRID_SIZE - 2)).toInt() + 1)
                PlanetaryEntityFactory.spawnUnit(world, ctx, pick, sx, sy)
            }
        } else {
            raidSpawnAccumulator = 0f
        }

        val allUnits = em.getEntitiesWith3<Position, Velocity, UnitComponent>()
        val playerUnits = allUnits.filter { it.c.faction == UnitFaction.PLAYER && it.c.hp > 0 }
        val enemyUnits = allUnits.filter { it.c.faction == UnitFaction.ENEMY && it.c.hp > 0 }
        val structures = em.getEntitiesWith2<TransformComponent, StructureComponent>()

        // Find Orbital Hub anchor for player idle patrol orbit
        val hubStruct = structures.firstOrNull { it.b.buildingType == BuildingType.ORBITAL_HUB }
        val hubX = hubStruct?.a?.x?.toFloat() ?: (GRID_SIZE / 2f)
        val hubY = hubStruct?.a?.y?.toFloat() ?: (GRID_SIZE / 2f)

        // 3. Auto-Turrets actively intercept real enemy units within range
        if (enemyUnits.isNotEmpty()) {
            turretFireAccumulator += dtSeconds
            if (turretFireAccumulator >= 0.75f) {
                turretFireAccumulator -= 0.75f
                for (struct in structures) {
                    if (struct.b.buildingType != BuildingType.AUTO_TURRET || struct.b.integrity <= 0) continue
                    val nearestEnemy = enemyUnits
                        .filter { it.c.hp > 0 }
                        .minByOrNull { hypot(it.a.x - struct.a.x, it.a.y - struct.a.y) }
                        ?: continue
                    val dist = hypot(nearestEnemy.a.x - struct.a.x, nearestEnemy.a.y - struct.a.y)
                    if (dist <= TURRET_RANGE_TILES) {
                        nearestEnemy.c.hp = (nearestEnemy.c.hp - TURRET_DAMAGE).coerceAtLeast(0)
                        val beamEntity = em.createEntity()
                        em.addComponent(
                            beamEntity,
                            TurretBeamComponent(
                                fromX = struct.a.x,
                                fromY = struct.a.y,
                                targetX = nearestEnemy.a.x,
                                targetY = nearestEnemy.a.y,
                                ttlSeconds = 0.35f
                            )
                        )
                    }
                }
            }
        }

        // 4. Player (UEC) Unit AI: Repair support -> Intercept Hostiles -> Patrol Hub
        for (item in playerUnits) {
            val pos = item.a
            val vel = item.b
            val unit = item.c
            unit.cooldownTimer = (unit.cooldownTimer - dtSeconds).coerceAtLeast(0f)

            // Support unit priority: repair damaged buildings or wounded allies
            if (unit.repairPerSecond > 0f) {
                val damagedBuilding = structures
                    .filter { it.b.integrity < it.b.maxIntegrity }
                    .minByOrNull { hypot(it.a.x - pos.x, it.a.y - pos.y) }
                if (damagedBuilding != null) {
                    val dx = damagedBuilding.a.x - pos.x
                    val dy = damagedBuilding.a.y - pos.y
                    val dist = hypot(dx, dy)
                    if (dist <= unit.attackRange) {
                        vel.dx = 0f
                        vel.dy = 0f
                        unit.statusNote = "REPAIRING"
                        if (unit.cooldownTimer <= 0f) {
                            val heal = max(3, (unit.repairPerSecond * 0.5f).toInt())
                            damagedBuilding.b.integrity =
                                (damagedBuilding.b.integrity + heal).coerceAtMost(damagedBuilding.b.maxIntegrity)
                            unit.cooldownTimer = 0.5f
                            spawnBeam(em, pos.x, pos.y, damagedBuilding.a.x.toFloat(), damagedBuilding.a.y.toFloat(), isPlayer = true, isRepair = true)
                        }
                    } else {
                        steerTowards(vel, dx, dy, dist, unit.moveSpeed)
                        unit.statusNote = "EN ROUTE"
                    }
                    continue
                }

                val woundedAlly = playerUnits
                    .filter { it.entity != item.entity && it.c.hp in 1 until it.c.maxHp }
                    .minByOrNull { hypot(it.a.x - pos.x, it.a.y - pos.y) }
                if (woundedAlly != null) {
                    val dx = woundedAlly.a.x - pos.x
                    val dy = woundedAlly.a.y - pos.y
                    val dist = hypot(dx, dy)
                    if (dist <= unit.attackRange) {
                        vel.dx = 0f
                        vel.dy = 0f
                        unit.statusNote = "WELDING"
                        if (unit.cooldownTimer <= 0f) {
                            val heal = max(4, (unit.repairPerSecond * 0.5f).toInt())
                            woundedAlly.c.hp = (woundedAlly.c.hp + heal).coerceAtMost(woundedAlly.c.maxHp)
                            unit.cooldownTimer = 0.5f
                            spawnBeam(em, pos.x, pos.y, woundedAlly.a.x, woundedAlly.a.y, isPlayer = true, isRepair = true)
                        }
                    } else {
                        steerTowards(vel, dx, dy, dist, unit.moveSpeed)
                        unit.statusNote = "ASSISTING"
                    }
                    continue
                }
            }

            // Engage nearest living enemy
            val targetEnemy = enemyUnits
                .filter { it.c.hp > 0 }
                .minByOrNull { hypot(it.a.x - pos.x, it.a.y - pos.y) }

            if (targetEnemy != null) {
                val dx = targetEnemy.a.x - pos.x
                val dy = targetEnemy.a.y - pos.y
                val dist = hypot(dx, dy)
                if (dist <= unit.attackRange) {
                    vel.dx = 0f
                    vel.dy = 0f
                    unit.statusNote = "ENGAGING"
                    if (unit.cooldownTimer <= 0f) {
                        targetEnemy.c.hp = (targetEnemy.c.hp - unit.attackDamage).coerceAtLeast(0)
                        unit.cooldownTimer = unit.attackCooldownSeconds
                        spawnBeam(em, pos.x, pos.y, targetEnemy.a.x, targetEnemy.a.y, isPlayer = true, isRepair = false)
                    }
                } else {
                    steerTowards(vel, dx, dy, dist, unit.moveSpeed)
                    unit.statusNote = "INTERCEPT"
                }
            } else {
                // Smooth circular patrol orbit around the Orbital Hub
                unit.patrolAngleRad += dtSeconds * 0.55f
                val orbitRadius = 2.3f + (unit.unitId % 3) * 0.65f
                val targetX = (hubX + cos(unit.patrolAngleRad) * orbitRadius).coerceIn(0.6f, GRID_SIZE - 1.6f)
                val targetY = (hubY + sin(unit.patrolAngleRad) * orbitRadius).coerceIn(0.6f, GRID_SIZE - 1.6f)
                val dx = targetX - pos.x
                val dy = targetY - pos.y
                val dist = hypot(dx, dy)
                if (dist > 0.12f) {
                    steerTowards(vel, dx, dy, dist, unit.moveSpeed * 0.65f)
                } else {
                    vel.dx = 0f
                    vel.dy = 0f
                }
                unit.statusNote = "PATROL"
            }
        }

        // 5. Enemy (Xeno) Unit AI: Engage nearby UEC defenders or assault planetary structures
        for (item in enemyUnits) {
            val pos = item.a
            val vel = item.b
            val unit = item.c
            if (unit.hp <= 0) continue
            unit.cooldownTimer = (unit.cooldownTimer - dtSeconds).coerceAtLeast(0f)

            val nearestPlayer = playerUnits
                .filter { it.c.hp > 0 }
                .minByOrNull { hypot(it.a.x - pos.x, it.a.y - pos.y) }
            val playerDist = nearestPlayer?.let { hypot(it.a.x - pos.x, it.a.y - pos.y) } ?: Float.MAX_VALUE

            val nearestBuilding = structures
                .filter { it.b.integrity > 15 }
                .minByOrNull { hypot(it.a.x - pos.x, it.a.y - pos.y) }
            val buildingDist = nearestBuilding?.let { hypot(it.a.x - pos.x, it.a.y - pos.y) } ?: Float.MAX_VALUE

            // Prioritize player units if closer or within immediate aggro radius
            if (nearestPlayer != null && (playerDist <= buildingDist + 1.2f || nearestBuilding == null)) {
                val dx = nearestPlayer.a.x - pos.x
                val dy = nearestPlayer.a.y - pos.y
                if (playerDist <= unit.attackRange) {
                    vel.dx = 0f
                    vel.dy = 0f
                    unit.statusNote = "ATTACKING"
                    if (unit.cooldownTimer <= 0f) {
                        nearestPlayer.c.hp = (nearestPlayer.c.hp - unit.attackDamage).coerceAtLeast(0)
                        unit.cooldownTimer = unit.attackCooldownSeconds
                        spawnBeam(em, pos.x, pos.y, nearestPlayer.a.x, nearestPlayer.a.y, isPlayer = false, isRepair = false)
                    }
                } else {
                    steerTowards(vel, dx, dy, playerDist, unit.moveSpeed)
                    unit.statusNote = "ADVANCING"
                }
            } else if (nearestBuilding != null) {
                val dx = nearestBuilding.a.x - pos.x
                val dy = nearestBuilding.a.y - pos.y
                if (buildingDist <= unit.attackRange) {
                    vel.dx = 0f
                    vel.dy = 0f
                    unit.statusNote = "SIEGING"
                    if (unit.cooldownTimer <= 0f) {
                        val dmg = max(2, unit.attackDamage / 2)
                        nearestBuilding.b.integrity = (nearestBuilding.b.integrity - dmg).coerceAtLeast(15)
                        nearestBuilding.b.statusNote = "UNDER ATTACK"
                        unit.cooldownTimer = unit.attackCooldownSeconds
                        spawnBeam(em, pos.x, pos.y, nearestBuilding.a.x.toFloat(), nearestBuilding.a.y.toFloat(), isPlayer = false, isRepair = false)
                    }
                } else {
                    steerTowards(vel, dx, dy, buildingDist, unit.moveSpeed)
                    unit.statusNote = "ASSAULT"
                }
            } else {
                vel.dx = 0f
                vel.dy = 0f
            }
        }

        // 6. Clean up eliminated units and credit UEC bounties
        val eliminated = em.getEntitiesWith<UnitComponent>().filter { (_, u) -> u.hp <= 0 }
        for ((entity, unit) in eliminated) {
            if (unit.faction == UnitFaction.ENEMY) {
                ctx.pendingBountyCredits += unit.archetype.creditBounty
                ctx.enemiesEliminatedCount++
            }
            em.removeEntity(entity)
        }
    }

    private fun steerTowards(vel: Velocity, dx: Float, dy: Float, dist: Float, speed: Float) {
        if (dist <= 0.0001f) {
            vel.dx = 0f
            vel.dy = 0f
            return
        }
        vel.dx = (dx / dist) * speed
        vel.dy = (dy / dist) * speed
        vel.speedTilesPerSec = speed
    }

    private fun spawnBeam(
        em: EntityManager,
        fromX: Float,
        fromY: Float,
        targetX: Float,
        targetY: Float,
        isPlayer: Boolean,
        isRepair: Boolean
    ) {
        val beamEntity = em.createEntity()
        em.addComponent(
            beamEntity,
            CombatBeamComponent(
                fromX = fromX,
                fromY = fromY,
                targetX = targetX,
                targetY = targetY,
                isPlayer = isPlayer,
                isRepair = isRepair,
                ttlSeconds = 0.25f
            )
        )
    }

    companion object {
        private const val MAX_RAID_ENEMIES = 6
        private const val TURRET_RANGE_TILES = 4.6f
        private const val TURRET_DAMAGE = 22

        fun perimeterSpawnPoint(edge: Int, offset: Int): Pair<Float, Float> {
            val clamped = offset.coerceIn(1, GRID_SIZE - 2).toFloat()
            val maxEdge = (GRID_SIZE - 1).toFloat()
            return when (edge % 4) {
                0 -> clamped to 0.2f
                1 -> maxEdge - 0.2f to clamped
                2 -> clamped to maxEdge - 0.2f
                else -> 0.2f to clamped
            }
        }
    }
}
