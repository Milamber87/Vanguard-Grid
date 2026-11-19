const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "commander_alice_01";
const BOB_UID = "commander_bob_02";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validProfilePayload(uid, ts) {
  return {
    userId: uid,
    callsign: "Cmdr. Vance",
    activePlanetId: "temperate",
    uecCredits: 250,
    colonizationScore: 40,
    contractsCompleted: 1,
    hazardsSurvived: 2,
    unlockedTechIds: ["t1_extraction", "t1_logistics"],
    createdAt: ts,
    updatedAt: ts,
  };
}

function validPlanetPayload(uid, planetId, ts) {
  return {
    userId: uid,
    planetId,
    biomeName: "Aethelgard Basin",
    colonizationPercent: 18,
    powerGeneratedMw: 45,
    powerConsumedMw: 24,
    buildings: [
      {
        x: 6,
        y: 6,
        type: "ORBITAL_HUB",
        dir: "NORTH",
        integrity: 100,
        recipe: "NONE",
      },
      {
        x: 3,
        y: 5,
        type: "DRILL_MK1",
        dir: "EAST",
        integrity: 100,
        recipe: "IRON_ORE",
      },
    ],
    inventory: {
      IRON_ORE: 60,
      IRON_PLATE: 35,
      COPPER_ORE: 40,
      COAL: 25,
    },
    activeHazardId: "none",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validEventLogPayload(uid, ts) {
  return {
    userId: uid,
    planetId: "volcanic",
    eventType: "supply_contract",
    title: "7th Orbital Division Ammo Drop",
    outcome: "Dispatched 12 Munitions Crates via Orbital Hub (+320 UEC Credits).",
    creditsDelta: 320,
    createdAt: ts,
  };
}

test("Unauthenticated users cannot read or write any Vanguard Grid collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("planets").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("planets")
      .doc("temperate")
      .set(validPlanetPayload(ALICE_UID, "temperate", ts))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("eventLogs").get()
  );
});

test("Authenticated owner: valid CommanderProfile create & partial update succeed; shadow/immutable mutations fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  // Valid create matching Kotlin CloudSyncRepository.createProfile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, created))
  );

  // Valid get by owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Root /users list is forbidden for privacy
  await assertFails(aliceDb.collection("users").get());

  // Valid partial update matching Kotlin CloudSyncRepository.updateProfile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activePlanetId: "volcanic",
      uecCredits: 640,
      colonizationScore: 120,
      contractsCompleted: 3,
      hazardsSurvived: 4,
      unlockedTechIds: ["t1_extraction", "t1_logistics", "t1_steel"],
      updatedAt: updated,
    })
  );

  // Reject mutating immutable createdAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: updated,
      updatedAt: updated,
    })
  );

  // Reject shadow/ghost field injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: updated,
    })
  );

  // Reject future tampered timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      uecCredits: 999,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject invalid planet ID enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activePlanetId: "mars_invalid",
      updatedAt: updated,
    })
  );

  // Reject @DocumentId 'id' injected into raw payload on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .set({ ...validProfilePayload(BOB_UID, created), id: BOB_UID })
  );
});

test("Authenticated owner: valid PlanetColony create, list, and partial update succeed; bounds enforced", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  // Empty subcollection list query succeeds (mirrors observePlanets)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("planets").get()
  );

  // Create temperate colony
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("planets")
      .doc("temperate")
      .set(validPlanetPayload(ALICE_UID, "temperate", created))
  );

  // Partial update matching Kotlin savePlanetState
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("planets")
      .doc("temperate")
      .update({
        colonizationPercent: 34,
        powerGeneratedMw: 60,
        powerConsumedMw: 32,
        activeHazardId: "acid_squall",
        updatedAt: updated,
      })
  );

  // Reject out-of-range colonizationPercent (> 100)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("planets")
      .doc("temperate")
      .update({
        colonizationPercent: 150,
        updatedAt: updated,
      })
  );

  // Reject invalid planet doc ID
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("planets")
      .doc("unknown_world")
      .set(validPlanetPayload(ALICE_UID, "unknown_world", created))
  );
});

test("Authenticated owner: MissionEventLog is append-only and validated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("eventLogs")
      .doc("evt_001")
      .set(validEventLogPayload(ALICE_UID, ts))
  );

  // List query matching Kotlin observeEventLogs
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("eventLogs")
      .orderBy("createdAt", "desc")
      .limit(30)
      .get()
  );

  // Updating historical event log is forbidden (append-only)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("eventLogs")
      .doc("evt_001")
      .update({ creditsDelta: 99999 })
  );

  // Invalid eventType rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("eventLogs")
      .doc("evt_002")
      .set({ ...validEventLogPayload(ALICE_UID, ts), eventType: "invalid_cat" })
  );
});

test("Cross-user isolation: Alice cannot read, list, or mutate Bob's profile, planets, or logs", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID, ts));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("planets")
      .doc("volcanic")
      .set(validPlanetPayload(BOB_UID, "volcanic", ts));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("eventLogs")
      .doc("bob_evt")
      .set(validEventLogPayload(BOB_UID, ts));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).update({ uecCredits: 0, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("planets").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("planets").doc("volcanic").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("eventLogs").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("eventLogs").doc("bob_evt").delete()
  );
});
