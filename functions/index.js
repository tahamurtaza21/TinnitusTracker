const functions = require("firebase-functions");
const nodemailer = require("nodemailer");

const gmailEmail = "tahamurtaza15@gmail.com";
const gmailAppPassword = "alugsfyqxkbwpbcz";

const transporter = nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: gmailEmail,
    pass: gmailAppPassword,
  },
});

exports.sendDoctorReport = functions.https.onCall(async (data, context) => {
  try {
    functions.logger.info("UID:", context.auth?.uid || "unauthenticated");


   const rawData = data?.data ?? data; // Handle both wrapped and unwrapped
   const email = rawData.email;
   const fileUrl = rawData.fileUrl;

//    // ✅ Extra check to see if data is structured as expected
//    if (typeof email !== "string" || typeof fileUrl !== "string") {
//      throw new functions.https.HttpsError("invalid-argument", "email or fileUrl is not a string");
//    }

    functions.logger.info("Email to send to:", email);
    functions.logger.info("PDF download link:", fileUrl);
    functions.logger.info("PDF download link:", fileUrl);

    const mailOptions = {
      from: `Tinnitus Report <${gmailEmail}>`,
      to: email,
      subject: "Tinnitus Progress Report",
      text: "Attached is the latest tinnitus/anxiety report PDF.",
      attachments: [
        {
          filename: "report.pdf",
          path: fileUrl,
        },
      ],
    };

    functions.logger.info("Final recipient:", email);

    await transporter.sendMail(mailOptions);
    functions.logger.info("✅ Email sent successfully");
    return { success: true };

  } catch (error) {
    functions.logger.error("❌ Email send failed", error);
    throw new functions.https.HttpsError("internal", error.message || "Failed to send email");
  }
});
