/**
 * Deteksi gejala darurat (Bahasa Indonesia) sebelum memanggil model.
 *
 * Darurat bila ada kata gejala berat DAN salah satu:
 *  - penanda kuat bahwa sedang terjadi ("sekarang", "barusan", "dari tadi", "tolong", ...), atau
 *  - pernyataan langsung orang pertama/keluarga ("saya nyeri dada", "dada saya sakit", "bapak saya pingsan")
 *    atau penanda lemah ("tiba-tiba", "mendadak"), selama kalimat bukan pengandaian ("kalau/jika ...").
 * Pertanyaan edukasi umum ("apa tanda serangan jantung?", "apakah keringat dingin tanda serangan jantung?")
 * tidak dianggap darurat — model tetap diarahkan oleh prompt sistem untuk menyebut 119/112 bila relevan.
 */

const SYMPTOMS: RegExp[] = [
  /\b(nyeri|sakit)\s+(di\s+|pada\s+)?dada/,
  /\bdada(ku)?\s+(saya\s+|aku\s+|kiri\s+|tengah\s+)?(terasa\s+|rasanya\s+|seperti\s+|kok\s+|jadi\s+)?(sakit|nyeri|tertekan|ditekan|tertindih|ditindih|sesak|berat|panas|terbakar|diremas|ngilu)/,
  /\bsesak\s+(napas|nafas)\s+(yang\s+)?(berat|parah|hebat|sekali|banget)/,
  /\b(tidak|tak|gak|nggak|ga|ndak)\s+bisa\s+(ber)?(napas|nafas)/,
  /\b(susah|sulit|sukar)\s+(ber)?(napas|nafas)\s+(berat|parah|sekali|banget)/,
  /\b(pingsan|tidak\s+sadarkan\s+diri|tak\s+sadarkan\s+diri|hilang\s+kesadaran)/,
  /\bkeringat\s+dingin/,
  /\b(lumpuh|lemah|lemas|mati\s+rasa|kesemutan)\s+(separuh|sebelah|setengah|satu\s+sisi)/,
  /\b(separuh|sebelah)\s+(badan|tubuh)\s+(lumpuh|lemah|lemas|mati\s+rasa)/,
  /\b(bicara|ngomong|bicaranya|omongan(nya)?)\s+(jadi\s+)?(pelo|cadel|tidak\s+jelas|kacau)/,
  /\b(wajah|muka|mulut|bibir)\s+(jadi\s+)?(mencong|perot|merot|miring|turun\s+sebelah)/,
  /\bkejang/,
];

const PRONOUN = String.raw`(saya|aku|sy|gue|gw|kami|(bapak|ibu|ayah|suami|istri|anak|kakek|nenek|mertua|adik|kakak|tetangga|teman)(ku|\s+(saya|aku|kami))?)`;

/** Penanda kuat: gejala sedang/baru saja terjadi. */
const STRONG_NOW = /\b(sekarang|skrg|barusan|baru\s+saja|sejak\s+tadi|dari\s+tadi|sudah\s+\d+\s*(menit|jam)|tolong|darurat|gawat|sedang\s+(merasa|mengalami|terasa)|lagi\s+(merasa|mengalami|terasa))\b/;
/** Penanda lemah: deskripsi mendadak (bisa juga dipakai dalam pengandaian). */
const WEAK_NOW = /\b(mendadak|tiba[\s-]?tiba|tadi)\b/;
const CONDITIONAL = /\b(kalau|kalo|klo|jika|jikalau|bila|apabila|seandainya|andaikan|andai|misalnya|misal|umpama|semisal)\b/;

/** Bentuk pertanyaan umum (dipakai hanya untuk penanda lemah). */
const QUESTION = /\b(apakah|apa|bagaimana|gimana|mengapa|kenapa|benarkah|bisakah)\b/;

const DIRECT: RegExp[] = SYMPTOMS.map(
  (s) => new RegExp(String.raw`\b${PRONOUN}\s+(\S+\s+){0,4}?${s.source.replace(/^\\b/, '')}`),
);
const SELF_IN_SYMPTOM = /\b(dadaku|dada\s+(saya|aku))\b/;

function normalize(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFKC')
    .replace(/[^\p{L}\p{N}\s-]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function isEmergency(text: string): boolean {
  const t = normalize(text);
  if (!SYMPTOMS.some((r) => r.test(t))) return false;
  if (STRONG_NOW.test(t)) return true;
  if (CONDITIONAL.test(t)) return false;
  if (SELF_IN_SYMPTOM.test(t) || DIRECT.some((r) => r.test(t))) return true;
  return WEAK_NOW.test(t) && !QUESTION.test(t) && !text.includes('?');
}
