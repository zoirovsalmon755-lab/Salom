package com.viltrumite.boss.entity;

import com.viltrumite.boss.ModEntities;
import com.viltrumite.boss.ModParticles;
import com.viltrumite.boss.Taunts;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * Боси "Витрумит": ҷон 1200, дастгир кардан + парвоз + партофтан, муаллақ кардани блокҳо,
 * блок (parry), флеш-даш, клонҳо, зарбаи замин ва суханҳо дар чат.
 */
public class ViltrumiteEntity extends HostileEntity {
	// ---- ҳолатҳо (барои анимация ба клиент ҳам фиристода мешаванд) ----
	public static final int ST_IDLE = 0;
	public static final int ST_GRAB = 1;
	public static final int ST_THROW = 2;
	public static final int ST_BLOCK = 3;
	public static final int ST_PUNCH = 4;
	public static final int ST_SLAM = 5;
	public static final int ST_BLOCKS = 6;
	public static final int ST_DASH = 7;
	public static final int ST_SUMMON = 8;

	private static final TrackedData<Integer> STATE =
			DataTracker.registerData(ViltrumiteEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> CLONE =
			DataTracker.registerData(ViltrumiteEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	// ---- ТАНЗИМОТ: ин рақамҳоро озодона иваз кунед ----
	private static final float SLAM_DAMAGE = 11f;          // зарбаи замин (дар радиуси 8 блок)
	private static final float THROW_IMPACT_DAMAGE = 13f;  // зарба ҳангоми афтидан пас аз партоб
	private static final float BLOCK_HIT_DAMAGE = 9f;      // блоки партофташуда
	private static final float PARRY_CHANCE = 0.5f;        // эҳтимоли блок кардани зарбаи шумо
	private static final int MAX_CLONES = 3;
	private static final int CLONE_LIFETIME = 400;         // тик (20 тик = 1 сония)
	private static final double FLY_SPEED = 1.85;          // блок дар як тик, ҳангоми парвози мутлақ
	private static final int ASCEND_TICKS = 16;
	private static final int FLY_TICKS = 32;

	private final ServerBossBar bossBar =
			new ServerBossBar(Text.literal("Витрумит"), BossBar.Color.YELLOW, BossBar.Style.NOTCHED_10);

	// ---- ҳолати мантиқӣ (танҳо сервер) ----
	private int stateTicks;
	private int grabPhase;
	private int phaseStart;
	private int cdGrab, cdSlam, cdBlocks, cdDash, cdClones, cdPunch, cdParry;
	private int sayCd, chaseTalkCd, lowCd, fleeTicks, punchCount, thrownTicks;
	private boolean enraged, greeted, deadTaunted, counterPending;
	private float flightYaw;
	private float thrownBonus;
	private LivingEntity grabbed;
	private LivingEntity thrown;
	private LivingEntity lastTarget;
	private final List<FallingBlockEntity> orbit = new ArrayList<>();
	private final List<Shot> shots = new ArrayList<>();
	private final Set<UUID> clones = new HashSet<>();

	// ---- барои анимацияи клиент ----
	public float lean;
	public float prevLean;
	public int stateStartAge;

	private static class Shot {
		final FallingBlockEntity block;
		int ttl = 45;

		Shot(FallingBlockEntity block) {
			this.block = block;
		}
	}

	public ViltrumiteEntity(EntityType<? extends ViltrumiteEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 500;
		this.setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 1200.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.42)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 14.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_ARMOR, 10.0)
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5);
	}

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		this.dataTracker.startTracking(STATE, ST_IDLE);
		this.dataTracker.startTracking(CLONE, false);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new SwimGoal(this));
		this.goalSelector.add(2, new ChaseGoal(this));
		this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 32.0f));
		this.goalSelector.add(8, new LookAroundGoal(this));
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	/** Мисли зомби ба сӯи ҳадаф меравад, вақте ки қобилият истифода намешавад. */
	private static class ChaseGoal extends Goal {
		private final ViltrumiteEntity mob;
		private int repath;

		ChaseGoal(ViltrumiteEntity mob) {
			this.mob = mob;
			this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity t = mob.getTarget();
			return t != null && t.isAlive() && mob.getState() == ST_IDLE;
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			LivingEntity t = mob.getTarget();
			if (t == null) {
				return;
			}
			mob.getLookControl().lookAt(t, 30.0f, 30.0f);
			if (--repath <= 0) {
				repath = 4;
				mob.getNavigation().startMovingTo(t, 1.5);   // суръати "флеш" ҳангоми таъқиб
			}
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}
	}

	// =====================================================================
	//  Клон
	// =====================================================================
	public boolean isClone() {
		return this.dataTracker.get(CLONE);
	}

	public void makeClone() {
		this.dataTracker.set(CLONE, true);
		this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(24.0);
		this.setHealth(24.0f);
		this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(6.0);
		this.experiencePoints = 0;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("IsClone", this.isClone());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.getBoolean("IsClone")) {
			this.makeClone();
		}
	}

	// =====================================================================
	//  Ҳолат
	// =====================================================================
	public int getState() {
		return this.dataTracker.get(STATE);
	}

	private void setState(int s) {
		this.stateTicks = 0;
		this.dataTracker.set(STATE, s);
	}

	private void endState() {
		releaseGrabbed();
		clearOrbit();
		this.setNoGravity(false);
		setState(ST_IDLE);
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (STATE.equals(data)) {
			this.stateStartAge = this.age;
		}
	}

	// =====================================================================
	//  Tick (ҳам клиент, ҳам сервер)
	// =====================================================================
	@Override
	public void tick() {
		super.tick();
		this.prevLean = this.lean;
		double dx = this.getX() - this.prevX;
		double dy = this.getY() - this.prevY;
		double dz = this.getZ() - this.prevZ;
		double hs = Math.sqrt(dx * dx + dz * dz);
		int st = getState();
		float targetLean = 0f;
		if (st == ST_GRAB) {
			targetLean = (float) Math.min(1.0, hs / 0.9);
		} else if (st == ST_DASH) {
			targetLean = 0.35f;
		} else if (hs > 0.35) {
			targetLean = 0.25f;
		}
		this.lean += (targetLean - this.lean) * 0.3f;

		if (this.getWorld().isClient) {
			spawnTrail(dx, dy, dz);
		}
	}

	/** Дуди сиёҳ аз қафо. Шумораи зарраҳо маҳдуд аст, то FPS наафтад. */
	private void spawnTrail(double dx, double dy, double dz) {
		double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (speed < 0.18) {
			return;
		}
		int n = speed > 0.9 ? 3 : (speed > 0.45 ? 2 : (this.age % 2 == 0 ? 1 : 0));
		double bx = -dx / speed;
		double by = -dy / speed;
		double bz = -dz / speed;
		for (int i = 0; i < n; i++) {
			double ox = (this.random.nextDouble() - 0.5) * 0.4;
			double oy = this.random.nextDouble() * 0.9;
			double oz = (this.random.nextDouble() - 0.5) * 0.4;
			this.getWorld().addParticle(ModParticles.VILTRUMITE_SMOKE,
					this.getX() + bx * 0.5 + ox,
					this.getY() + 0.25 + by * 0.5 + oy,
					this.getZ() + bz * 0.5 + oz,
					bx * 0.03, 0.01, bz * 0.03);
		}
	}

	// =====================================================================
	//  Мағзи AI (танҳо сервер)
	// =====================================================================
	@Override
	protected void mobTick() {
		super.mobTick();
		if (this.getWorld().isClient) {
			return;
		}
		ServerWorld sw = (ServerWorld) this.getWorld();

		if (isClone()) {
			if (this.age > CLONE_LIFETIME) {
				poof(sw);
				this.discard();
				return;
			}
		} else {
			this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());
		}

		cdGrab = dec(cdGrab);
		cdSlam = dec(cdSlam);
		cdBlocks = dec(cdBlocks);
		cdDash = dec(cdDash);
		cdClones = dec(cdClones);
		cdPunch = dec(cdPunch);
		cdParry = dec(cdParry);
		if (sayCd > 0) sayCd--;
		if (lowCd > 0) lowCd--;

		LivingEntity target = this.getTarget();
		if (target != null && !target.isAlive()) {
			target = null;
		}
		if (target != null) {
			lastTarget = target;
			deadTaunted = false;
			talkLogic(target);
		}
		if (lastTarget != null && !lastTarget.isAlive() && !deadTaunted) {
			deadTaunted = true;
			say(Taunts.Type.PLAYER_DEAD, true);
			lastTarget = null;
		}

		updateShots(sw);
		updateThrown(sw);

		int st = getState();
		if (st == ST_IDLE) {
			if (target != null) {
				chooseAbility(sw, target);
			}
			return;
		}
		stateTicks++;
		switch (st) {
			case ST_PUNCH -> runPunch(sw, target);
			case ST_BLOCK -> runBlock(target);
			case ST_SLAM -> runSlam(sw, target);
			case ST_GRAB -> runGrab(sw, target);
			case ST_THROW -> runThrow();
			case ST_BLOCKS -> runBlocks(sw, target);
			case ST_DASH -> runDash(sw, target);
			case ST_SUMMON -> runSummon(sw, target);
			default -> endState();
		}
	}

	private int dec(int v) {
		return Math.max(0, v - (enraged ? 2 : 1));
	}

	private void chooseAbility(ServerWorld sw, LivingEntity target) {
		float d = this.distanceTo(target);
		boolean see = this.canSee(target);
		if (isClone()) {
			if (d <= 3.2f && cdPunch == 0) startPunch();
			return;
		}
		float r = this.random.nextFloat();
		if (d <= 3.4f) {
			if (cdGrab == 0 && r < 0.05f) startGrab();
			else if (cdSlam == 0 && r < 0.09f) startSlam();
			else if (cdPunch == 0) startPunch();
		} else if (d <= 10f) {
			if (cdBlocks == 0 && see && r < 0.04f) startBlocks(sw);
			else if (cdGrab == 0 && see && r < 0.09f) startGrab();
			else if (cdSlam == 0 && r < 0.12f) startSlam();
			else if (cdClones == 0 && r < 0.14f) startSummon();
		} else {
			if (cdDash == 0 && see && d < 45f && r < 0.07f) startDash();
			else if (cdBlocks == 0 && see && r < 0.12f) startBlocks(sw);
			else if (cdClones == 0 && r < 0.13f) startSummon();
		}
	}

	// ---------------------------------------------------------------------
	//  Суханҳо
	// ---------------------------------------------------------------------
	private void talkLogic(LivingEntity target) {
		if (isClone()) return;
		if (!greeted) {
			greeted = true;
			say(Taunts.Type.SPAWN, true);
		}
		float d = this.distanceTo(target);
		if (d > 18f) {
			fleeTicks++;
			if (fleeTicks % 140 == 40) say(Taunts.Type.FLEE, false);
		} else if (d < 12f) {
			fleeTicks = 0;
		}
		if (chaseTalkCd > 0) {
			chaseTalkCd--;
		} else if (d < 16f) {
			chaseTalkCd = 240 + this.random.nextInt(200);
			say(Taunts.Type.CHASE, false);
		}
		if (target instanceof PlayerEntity && target.getHealth() < 7f && lowCd == 0) {
			lowCd = 400;
			say(Taunts.Type.PLAYER_LOW, false);
		}
	}

	private void say(Taunts.Type type, boolean force) {
		if (isClone() || !(this.getWorld() instanceof ServerWorld sw)) return;
		if (!force && sayCd > 0) return;
		sayCd = 50;
		Taunts.say(sw, this, type);
	}

	// ---------------------------------------------------------------------
	//  Зарбаҳо (punch)
	// ---------------------------------------------------------------------
	private void startPunch() {
		setState(ST_PUNCH);
	}

	private void runPunch(ServerWorld sw, LivingEntity target) {
		if (target == null) {
			endState();
			return;
		}
		this.getNavigation().stop();
		if (stateTicks <= 3) this.lookAtEntity(target, 60f, 60f);
		if (stateTicks == 4 || stateTicks == 9) {
			if (this.distanceTo(target) <= 4.5f) {
				punchHit(sw, target, stateTicks == 9 && punchCount % 2 == 1, 1.0f);
			}
		}
		if (stateTicks >= 13) {
			punchCount++;
			cdPunch = enraged ? 3 : 7;
			endState();
		}
	}

	private void punchHit(ServerWorld sw, LivingEntity target, boolean uppercut, float mult) {
		float dmg = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * mult * (enraged ? 1.3f : 1f);
		if (uppercut) dmg *= 1.2f;
		boolean hit = target.damage(this.getDamageSources().mobAttack(this), dmg);
		if (hit) {
			target.takeKnockback(uppercut ? 0.9 : 1.4, this.getX() - target.getX(), this.getZ() - target.getZ());
			if (uppercut) target.addVelocity(0, 0.85, 0);
			syncVelocity(target);
			sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
			sw.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.0f, 0.8f);
			if (this.random.nextFloat() < 0.25f) say(Taunts.Type.HIT, false);
		}
	}

	// ---------------------------------------------------------------------
	//  Блок (parry)
	// ---------------------------------------------------------------------
	private void runBlock(LivingEntity target) {
		this.getNavigation().stop();
		if (stateTicks >= 10) {
			boolean counter = counterPending;
			counterPending = false;
			endState();
			if (counter && target != null && this.distanceTo(target) < 4.5f) {
				cdPunch = 0;
				startPunch();
			}
		}
	}

	// ---------------------------------------------------------------------
	//  Зарбаи замин (slam)
	// ---------------------------------------------------------------------
	private void startSlam() {
		setState(ST_SLAM);
		say(Taunts.Type.SLAM, false);
	}

	private void runSlam(ServerWorld sw, LivingEntity target) {
		if (stateTicks == 1) {
			Vec3d d = Vec3d.ZERO;
			double len = 0;
			if (target != null) {
				d = target.getPos().subtract(this.getPos()).multiply(1, 0, 1);
				len = d.length();
				if (len > 0.01) d = d.multiply(1.0 / len);
				this.lookAtEntity(target, 90f, 90f);
			}
			double sp = Math.min(0.9, len * 0.12);
			this.setVelocity(d.x * sp, 1.25, d.z * sp);
		}
		if (stateTicks > 6 && this.isOnGround()) {
			slamImpact(sw);
			cdSlam = 170;
			endState();
		} else if (stateTicks > 60) {
			cdSlam = 100;
			endState();
		}
	}

	private void slamImpact(ServerWorld sw) {
		sw.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.2, this.getZ(), 3, 1.5, 0.2, 1.5, 0.0);
		BlockState below = sw.getBlockState(this.getBlockPos().down());
		sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, below),
				this.getX(), this.getY() + 0.1, this.getZ(), 60, 1.8, 0.1, 1.8, 0.2);
		sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 0.7f);
		List<PlayerEntity> list = sw.getEntitiesByClass(PlayerEntity.class,
				this.getBoundingBox().expand(8, 3, 8), p -> p.isAlive() && !p.isSpectator());
		for (PlayerEntity p : list) {
			float d = this.distanceTo(p);
			float dmg = Math.max(3f, SLAM_DAMAGE * (1f - d / 10f));
			p.damage(this.getDamageSources().mobAttack(this), dmg);
			Vec3d away = p.getPos().subtract(this.getPos()).multiply(1, 0, 1);
			if (away.lengthSquared() < 1e-4) away = new Vec3d(0, 0, 1);
			away = away.normalize();
			pushEntity(p, away.x * 1.2, 0.7, away.z * 1.2);
		}
	}

	// ---------------------------------------------------------------------
	//  Дастгир кардан, парвоз, партофтан (viltrumite)
	// ---------------------------------------------------------------------
	private void startGrab() {
		setState(ST_GRAB);
		grabPhase = 0;
		grabbed = null;
	}

	private void runGrab(ServerWorld sw, LivingEntity target) {
		if (grabPhase == 0) {                // 0: ҳамла ба сӯи ҳадаф
			if (target == null) {
				cdGrab = 60;
				endState();
				return;
			}
			this.lookAtEntity(target, 90f, 90f);
			Vec3d dir = target.getPos().add(0, target.getHeight() * 0.4, 0)
					.subtract(this.getPos().add(0, this.getHeight() * 0.4, 0));
			if (dir.length() < 2.4) {
				grabTarget(sw, target);
				return;
			}
			Vec3d v = dir.normalize().multiply(1.15);
			this.setVelocity(v.x, v.y * 0.4, v.z);
			if (stateTicks > 14) {
				cdGrab = 80;
				endState();
			}
			return;
		}
		if (!ensureHeld()) {
			cdGrab = 120;
			endState();
			return;
		}
		int phaseTicks = stateTicks - phaseStart;
		this.setYaw(flightYaw);
		this.setBodyYaw(flightYaw);
		this.setHeadYaw(flightYaw);
		if (grabPhase == 1) {                // 1: боло рафтан
			this.setVelocity(0, 0.85, 0);
			if (phaseTicks >= ASCEND_TICKS || this.verticalCollision) {
				grabPhase = 2;
				phaseStart = stateTicks;
			}
		} else {                             // 2: парвози баландсуръат
			double rad = Math.toRadians(flightYaw);
			this.setVelocity(-Math.sin(rad) * FLY_SPEED, 0.03, Math.cos(rad) * FLY_SPEED);
			if (this.horizontalCollision) {
				releaseThrow(sw, true);
			} else if (phaseTicks >= FLY_TICKS) {
				releaseThrow(sw, false);
			}
		}
	}

	private void grabTarget(ServerWorld sw, LivingEntity target) {
		grabbed = target;
		target.startRiding(this, true);
		grabPhase = 1;
		phaseStart = stateTicks;
		flightYaw = this.random.nextFloat() * 360f - 180f;
		this.setNoGravity(true);
		this.setVelocity(Vec3d.ZERO);
		sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 1.2f, 0.7f);
		say(Taunts.Type.GRAB, true);
	}

	private boolean ensureHeld() {
		if (grabbed == null || !grabbed.isAlive()) {
			return false;
		}
		if (grabbed.getVehicle() != this) {
			grabbed.startRiding(this, true);
		}
		grabbed.fallDistance = 0f;
		return true;
	}

	private void releaseThrow(ServerWorld sw, boolean crash) {
		setState(ST_THROW);
		this.setNoGravity(false);
		if (grabbed != null) {
			grabbed.stopRiding();
			double rad = Math.toRadians(this.getYaw());
			pushEntity(grabbed, -Math.sin(rad) * 2.3, -1.5, Math.cos(rad) * 2.3);
			thrown = grabbed;
			thrownTicks = 0;
			thrownBonus = crash ? 4f : 0f;
			grabbed = null;
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 0.6f);
			say(Taunts.Type.THROW, true);
		}
		this.setVelocity(0, 0, 0);
	}

	private void runThrow() {
		if (stateTicks >= 14) {
			cdGrab = 260;
			endState();
		}
	}

	private void releaseGrabbed() {
		if (grabbed != null) {
			grabbed.stopRiding();
			grabbed = null;
		}
	}

	/** Назорати ҳадафи партофташуда: ҳангоми расидан ба замин зарба мезанад. */
	private void updateThrown(ServerWorld sw) {
		if (thrown == null) return;
		if (!thrown.isAlive()) {
			thrown = null;
			return;
		}
		thrownTicks++;
		thrown.fallDistance = 0f;
		if (thrownTicks > 3 && (thrown.isOnGround() || thrown.horizontalCollision || thrown.isTouchingWater())) {
			if (!thrown.isTouchingWater()) {
				thrown.damage(this.getDamageSources().mobAttack(this), THROW_IMPACT_DAMAGE + thrownBonus);
				sw.spawnParticles(ParticleTypes.EXPLOSION, thrown.getX(), thrown.getY() + 0.2, thrown.getZ(), 2, 0.6, 0.2, 0.6, 0.0);
				BlockState below = sw.getBlockState(thrown.getBlockPos().down());
				sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, below),
						thrown.getX(), thrown.getY() + 0.1, thrown.getZ(), 50, 0.8, 0.1, 0.8, 0.15);
				sw.playSound(null, thrown.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.6f, 0.8f);
				pushEntity(thrown, 0, 0.45, 0);
			}
			thrown = null;
		} else if (thrownTicks > 100) {
			thrown = null;
		}
	}

	/** Ҷои ҳадаф дар дасти босс (пеш аз ӯ, дар баландии сина). */
	@Override
	protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
		if (!this.hasPassenger(passenger)) return;
		double rad = Math.toRadians(this.getBodyYaw());
		positionUpdater.accept(passenger,
				this.getX() - Math.sin(rad) * 0.9,
				this.getY() + 1.1,
				this.getZ() + Math.cos(rad) * 0.9);
	}

	// ---------------------------------------------------------------------
	//  Блокҳои муаллақ ва партоби онҳо
	// ---------------------------------------------------------------------
	private void startBlocks(ServerWorld sw) {
		if (!sw.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
			cdBlocks = 200;
			return;
		}
		List<BlockPos> picks = pickBlocks(sw);
		if (picks.isEmpty()) {
			cdBlocks = 80;
			return;
		}
		for (BlockPos p : picks) {
			BlockState bs = sw.getBlockState(p);
			FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(sw, p, bs);
			fb.setNoGravity(true);
			fb.setVelocity(0, 0.4, 0);
			fb.velocityModified = true;
			orbit.add(fb);
		}
		setState(ST_BLOCKS);
		sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.HOSTILE, 1.0f, 0.6f);
		say(Taunts.Type.BLOCKS, false);
	}

	private List<BlockPos> pickBlocks(ServerWorld sw) {
		List<BlockPos> picks = new ArrayList<>();
		BlockPos c = this.getBlockPos();
		for (int i = 0; i < 40 && picks.size() < 4; i++) {
			BlockPos p = c.add(this.random.nextInt(9) - 4, this.random.nextInt(3) - 1, this.random.nextInt(9) - 4);
			BlockState s = sw.getBlockState(p);
			if (s.isAir() || !s.getFluidState().isEmpty()) continue;
			float h = s.getHardness(sw, p);
			if (h < 0 || h > 20) continue;
			if (sw.getBlockEntity(p) != null) continue;
			if (!s.isFullCube(sw, p)) continue;
			if (picks.contains(p)) continue;
			picks.add(p);
		}
		return picks;
	}

	private void runBlocks(ServerWorld sw, LivingEntity target) {
		if (target == null) {
			clearOrbit();
			endState();
			return;
		}
		this.getNavigation().stop();
		this.lookAtEntity(target, 40f, 40f);

		// блокҳо дар гирди босс давр мезананд
		for (int i = 0; i < orbit.size(); i++) {
			FallingBlockEntity fb = orbit.get(i);
			if (fb.isRemoved()) continue;
			double ang = (i / (double) orbit.size()) * Math.PI * 2 + stateTicks * 0.2;
			double hx = this.getX() + Math.cos(ang) * 2.2;
			double hy = this.getY() + 2.4 + Math.sin(stateTicks * 0.15 + i) * 0.3;
			double hz = this.getZ() + Math.sin(ang) * 2.2;
			fb.setVelocity((hx - fb.getX()) * 0.3, (hy - fb.getY()) * 0.3, (hz - fb.getZ()) * 0.3);
			fb.velocityModified = true;
		}

		// пас аз 28 тик, ҳар 4 тик як блокро ба сӯи ҳадаф мепартояд
		if (stateTicks >= 28 && (stateTicks - 28) % 4 == 0 && !orbit.isEmpty()) {
			FallingBlockEntity fb = orbit.remove(0);
			if (!fb.isRemoved()) {
				Vec3d aim = target.getEyePos().add(target.getVelocity().multiply(5));
				Vec3d v = aim.subtract(fb.getPos()).normalize().multiply(2.1);
				fb.setVelocity(v);
				fb.velocityModified = true;
				shots.add(new Shot(fb));
				sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.HOSTILE, 1.2f, 0.5f);
			}
		}
		if ((stateTicks >= 30 && orbit.isEmpty()) || stateTicks > 90) {
			cdBlocks = 150;
			endState();
		}
	}

	private void updateShots(ServerWorld sw) {
		Iterator<Shot> it = shots.iterator();
		while (it.hasNext()) {
			Shot s = it.next();
			FallingBlockEntity fb = s.block;
			if (fb.isRemoved()) {
				it.remove();
				continue;
			}
			s.ttl--;
			boolean burst = s.ttl <= 0 || fb.horizontalCollision || fb.verticalCollision;
			List<PlayerEntity> hitList = sw.getEntitiesByClass(PlayerEntity.class,
					fb.getBoundingBox().expand(0.5), p -> p.isAlive() && !p.isSpectator());
			if (!hitList.isEmpty()) {
				Vec3d v = fb.getVelocity();
				for (PlayerEntity p : hitList) {
					p.damage(this.getDamageSources().mobAttack(this), BLOCK_HIT_DAMAGE);
					pushEntity(p, v.x * 0.5, 0.35, v.z * 0.5);
				}
				burst = true;
			}
			if (burst) {
				sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, fb.getBlockState()),
						fb.getX(), fb.getY() + 0.5, fb.getZ(), 16, 0.3, 0.3, 0.3, 0.08);
				sw.playSound(null, fb.getBlockPos(), SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.2f, 0.8f);
				fb.discard();
				it.remove();
			}
		}
	}

	private void clearOrbit() {
		for (FallingBlockEntity fb : orbit) {
			if (!fb.isRemoved()) fb.discard();
		}
		orbit.clear();
	}

	// ---------------------------------------------------------------------
	//  Флеш-даш
	// ---------------------------------------------------------------------
	private void startDash() {
		setState(ST_DASH);
		say(Taunts.Type.DASH, false);
	}

	private void runDash(ServerWorld sw, LivingEntity target) {
		if (target == null) {
			endState();
			return;
		}
		this.getNavigation().stop();
		if (stateTicks == 1 && !teleportBehind(sw, target)) {
			cdDash = 40;
			endState();
			return;
		}
		if (stateTicks > 1 && stateTicks <= 18) this.lookAtEntity(target, 90f, 90f);
		if (stateTicks == 6 || stateTicks == 11 || stateTicks == 16) {
			if (this.distanceTo(target) <= 4.5f) punchHit(sw, target, stateTicks == 16, 0.7f);
		}
		if (stateTicks >= 20) {
			cdDash = 110;
			endState();
		}
	}

	private boolean teleportBehind(ServerWorld sw, LivingEntity target) {
		Vec3d start = this.getPos();
		Vec3d look = target.getRotationVector();
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		if (flat.lengthSquared() < 1e-4) flat = new Vec3d(0, 0, 1);
		flat = flat.normalize();
		Vec3d dest = target.getPos().subtract(flat.multiply(2.0));
		Box moved = this.getBoundingBox().offset(dest.x - this.getX(), dest.y - this.getY(), dest.z - this.getZ());
		if (!sw.isSpaceEmpty(this, moved)) {
			dest = target.getPos().add(flat.multiply(2.0));
			moved = this.getBoundingBox().offset(dest.x - this.getX(), dest.y - this.getY(), dest.z - this.getZ());
			if (!sw.isSpaceEmpty(this, moved)) return false;
		}
		for (int i = 0; i <= 16; i++) {
			Vec3d p = start.lerp(dest, i / 16.0);
			sw.spawnParticles(ModParticles.VILTRUMITE_SMOKE, p.x, p.y + 0.9, p.z, 1, 0.15, 0.4, 0.15, 0.0);
		}
		float yaw = (float) (MathHelper.atan2(target.getZ() - dest.z, target.getX() - dest.x) * 57.2957795) - 90f;
		this.refreshPositionAndAngles(dest.x, dest.y, dest.z, yaw, 0f);
		sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.8f, 1.6f);
		return true;
	}

	// ---------------------------------------------------------------------
	//  Клонҳо
	// ---------------------------------------------------------------------
	private void startSummon() {
		setState(ST_SUMMON);
		say(Taunts.Type.CLONES, true);
	}

	private void runSummon(ServerWorld sw, LivingEntity target) {
		this.getNavigation().stop();
		if (stateTicks == 8) spawnClones(sw, target);
		if (stateTicks >= 26) {
			cdClones = 420;
			endState();
		}
	}

	private void spawnClones(ServerWorld sw, LivingEntity target) {
		clones.removeIf(id -> sw.getEntity(id) == null);
		int need = MAX_CLONES - clones.size();
		for (int i = 0; i < need; i++) {
			double ang = this.random.nextDouble() * Math.PI * 2;
			double x = this.getX() + Math.cos(ang) * 2.5;
			double z = this.getZ() + Math.sin(ang) * 2.5;
			ViltrumiteEntity c = ModEntities.VILTRUMITE.create(sw);
			if (c == null) continue;
			c.refreshPositionAndAngles(x, this.getY(), z, this.getYaw(), 0f);
			if (!sw.isSpaceEmpty(c)) continue;
			c.makeClone();
			if (target != null) c.setTarget(target);
			sw.spawnEntity(c);
			clones.add(c.getUuid());
			poof(sw, x, this.getY() + 1, z);
		}
		sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.5f, 0.8f);
	}

	private void poof(ServerWorld sw) {
		poof(sw, this.getX(), this.getY() + 1, this.getZ());
	}

	private void poof(ServerWorld sw, double x, double y, double z) {
		sw.spawnParticles(ModParticles.VILTRUMITE_SMOKE, x, y, z, 8, 0.4, 0.6, 0.4, 0.0);
		sw.spawnParticles(ParticleTypes.POOF, x, y, z, 10, 0.4, 0.6, 0.4, 0.02);
	}

	// =====================================================================
	//  Зарар дидан: блок (parry), ғазаб, суханҳо
	// =====================================================================
	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.getWorld().isClient) {
			return super.damage(source, amount);
		}
		if (source.isIn(DamageTypeTags.IS_FALL)) {
			return false;
		}
		Entity attacker = source.getAttacker();
		boolean bypass = source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!bypass && !isClone() && attacker instanceof LivingEntity att && att != this) {
			int st = getState();
			if ((st == ST_IDLE || st == ST_PUNCH) && cdParry == 0 && this.random.nextFloat() < PARRY_CHANCE) {
				cdParry = 14;
				counterPending = true;
				setState(ST_BLOCK);
				this.getNavigation().stop();
				ServerWorld sw = (ServerWorld) this.getWorld();
				sw.playSound(null, this.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.4f, 0.7f);
				sw.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.2, this.getZ(), 14, 0.4, 0.5, 0.4, 0.3);
				say(Taunts.Type.BLOCK, false);
				return false;
			}
		}
		boolean result = super.damage(source, amount);
		if (result && !isClone()) {
			if (!enraged && this.getHealth() < this.getMaxHealth() * 0.33f) {
				enraged = true;
				this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.52);
				say(Taunts.Type.ENRAGE, true);
			} else if (this.random.nextFloat() < 0.2f) {
				say(Taunts.Type.BOSS_HIT, false);
			}
		}
		return result;
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (this.getWorld() instanceof ServerWorld sw) {
			endState();
			if (isClone()) {
				poof(sw);
			} else {
				say(Taunts.Type.DEATH, true);
				for (UUID id : clones) {
					Entity e = sw.getEntity(id);
					if (e != null) e.discard();
				}
				if (sw.getGameRules().getBoolean(GameRules.DO_MOB_LOOT)) {
					this.dropStack(new ItemStack(Items.DIAMOND, 16));
					this.dropStack(new ItemStack(Items.NETHER_STAR, 1));
				}
			}
		}
	}

	// =====================================================================
	//  Ёрирасонҳо
	// =====================================================================
	private static void pushEntity(Entity e, double x, double y, double z) {
		e.setVelocity(x, y, z);
		syncVelocity(e);
	}

	private static void syncVelocity(Entity e) {
		e.velocityModified = true;
		if (e instanceof ServerPlayerEntity sp) {
			sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
		}
	}

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		if (!isClone()) this.bossBar.addPlayer(player);
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		this.bossBar.removePlayer(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		releaseGrabbed();
		clearOrbit();
		this.bossBar.clearPlayers();
		super.remove(reason);
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}
}
