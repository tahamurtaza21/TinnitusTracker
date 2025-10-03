const admin = require("firebase-admin");

// Initialize Admin SDK with your service account
admin.initializeApp({
  credential: admin.credential.cert(require("./serviceAccountKey.json")),
});

const email = "nimisharaj80@gmail.com";

async function setAdmin() {
  try {
    // Lookup user by email
    const user = await admin.auth().getUserByEmail(email);

    // Assign custom claims
    await admin.auth().setCustomUserClaims(user.uid, { role: "admin" });
    console.log(`✅ ${email} is now an admin`);

    // Delete their Firestore "users" document
    const db = admin.firestore();
    await db.collection("users").doc(user.uid).delete();
    console.log(`🗑️ Deleted Firestore user doc for ${email}`);

    // add to admins collection
    await db.collection("admins").doc(user.uid).set({
      uid: user.uid,
      email: user.email,
      name: user.displayName || user.email.split("@")[0],
      signupDate: new Date()
    });

    process.exit(0);
  } catch (error) {
    console.error("❌ Error setting admin role:", error);
    process.exit(1);
  }
}

setAdmin();
