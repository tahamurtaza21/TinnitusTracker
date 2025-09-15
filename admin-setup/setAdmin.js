const admin = require("firebase-admin");

// Initialize Admin SDK with your service account
admin.initializeApp({
  credential: admin.credential.cert(require("./serviceAccountKey.json")),
});

// Email you want to make admin
const email = "tahamurtaza21@outlook.com";

async function setAdmin() {
  try {
    // Lookup user by email
    const user = await admin.auth().getUserByEmail(email);

    // Assign custom claims
    await admin.auth().setCustomUserClaims(user.uid, { role: "admin" });

    console.log(`✅ ${email} is now an admin`);
    process.exit(0);
  } catch (error) {
    console.error("❌ Error setting admin role:", error);
    process.exit(1);
  }
}

setAdmin();
