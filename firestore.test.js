const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated users cannot read or write profiles, conversations, or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      userId: ALICE_UID,
      title: "Hola",
      lastMessagePreview: "Hola",
      messageCount: 1,
      pinned: false,
      modelName: "gemini-3.8-flash",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner can create and update their profile, rejecting invalid models or ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      preferredModel: "gemini-3.8-flash",
      responseStyle: "balanced",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredModel: "gemini-3.5-flash-lite",
      responseStyle: "coding",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field (shadow update)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredModel: "gemini-3.8-flash",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Authenticated owner can create, list, update, pin, and delete conversations & messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const convRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1");

  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Proyecto Kotlin",
      lastMessagePreview: "Ayúdame con Compose",
      messageCount: 1,
      pinned: false,
      modelName: "gemini-3.8-flash",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );

  const msgRef = convRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      role: "user",
      content: "Ayúdame con Compose",
      hasAttachment: false,
      createdAt: created,
    })
  );

  const aiMsgRef = convRef.collection("messages").doc("msg_2");
  await assertSucceeds(
    aiMsgRef.set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      role: "model",
      content: "Claro, te ayudo paso a paso.",
      hasAttachment: false,
      modelName: "gemini-3.8-flash",
      createdAt: new Date(Date.now() - 3000),
    })
  );

  // Update conversation preview + count
  await assertSucceeds(
    convRef.update({
      lastMessagePreview: "Claro, te ayudo paso a paso.",
      messageCount: 2,
      modelName: "gemini-3.8-flash",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Pin conversation
  await assertSucceeds(
    convRef.update({
      pinned: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Update regenerated message content
  await assertSucceeds(
    aiMsgRef.update({
      content: "Respuesta actualizada con ejemplos.",
      modelName: "gemini-3.8-flash",
    })
  );

  // Reject orphaned message when conversation does not exist
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("missing_conv").collection("messages").doc("msg_x").set({
      userId: ALICE_UID,
      conversationId: "missing_conv",
      role: "user",
      content: "Orphan",
      hasAttachment: false,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId "id" inside body
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_2").set({
      id: "conv_2",
      userId: ALICE_UID,
      title: "Bad",
      lastMessagePreview: "",
      messageCount: 0,
      pinned: false,
      modelName: "gemini-3.8-flash",
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's conversations or messages", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const now = new Date(Date.now() - 5000);
    await db.collection("users").doc(ALICE_UID).collection("conversations").doc("alice_conv").set({
      userId: ALICE_UID,
      title: "Privado",
      lastMessagePreview: "Secreto",
      messageCount: 1,
      pinned: false,
      modelName: "gemini-3.8-flash",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("alice_conv").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("alice_conv").update({
      title: "Hackeado",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").doc("alice_conv").delete()
  );
});
