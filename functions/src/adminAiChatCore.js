function buildChatPrompt(payload) {
  const { query, languageTag, statsSnapshot } = payload;
  const isVi = String(languageTag || "").toLowerCase().startsWith("vi");

  return [
    "You are Sparkle, a data analyst assistant for a mobile photobooth app called 'Our Memories'.",
    "You have access to a snapshot of the dashboard metrics.",
    "Your goal is to answer the admin's question concisely based on the data.",
    "",
    "Rules:",
    "1) Use only the provided numeric data.",
    "2) If the data isn't enough to answer, say so politely.",
    "3) Keep the answer under 3 sentences unless specifically asked for details.",
    "4) Answer in the language matching languageTag.",
    `5) Admin's Question: "${query}"`,
    "",
    "Data Snapshot:",
    JSON.stringify(statsSnapshot),
    "",
    "Please provide the answer in the following JSON format: { \"answer\": \"...\" }"
  ].join("\n");
}

function normalizeChatResponse(rawText, languageTag) {
  const isVi = String(languageTag || "").toLowerCase().startsWith("vi");
  const fallback = isVi 
    ? "Xin lỗi, tôi gặp trục trặc khi xử lý dữ liệu." 
    : "Sorry, I had trouble processing the data.";

  try {
    const text = rawText.trim();
    let jsonContent = text;
    if (!text.startsWith("{")) {
      const start = text.indexOf("{");
      const end = text.lastIndexOf("}");
      if (start >= 0 && end > start) {
        jsonContent = text.slice(start, end + 1);
      }
    }
    const parsed = JSON.parse(jsonContent);
    return parsed.answer || fallback;
  } catch (e) {
    return fallback;
  }
}

module.exports = {
  buildChatPrompt,
  normalizeChatResponse
};
