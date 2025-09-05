import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Java 生成汉字拼音库（输出为 pinyin_database.json）
 * 覆盖常用汉字：Unicode 0x4E00-0x9FFF（约 2 万个汉字）
 * 包含格式：带声调拼音（如 zhōng）、不带声调拼音（如 zhong）
 */
public class PinyinDatabaseGenerator {

    // 配置拼音输出格式（带声调、小写、ü 用 u: 表示）
    private static final HanyuPinyinOutputFormat WITH_TONE_FORMAT;
    // 配置拼音输出格式（不带声调、小写、ü 用 u: 表示）
    private static final HanyuPinyinOutputFormat WITHOUT_TONE_FORMAT;

    static {
        // 初始化带声调的格式
        WITH_TONE_FORMAT = new HanyuPinyinOutputFormat();
        WITH_TONE_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE); // 小写
        WITH_TONE_FORMAT.setToneType(HanyuPinyinToneType.WITH_TONE_MARK); // 带声调符号（如 ā）
        WITH_TONE_FORMAT.setVCharType(HanyuPinyinVCharType.WITH_U_AND_COLON); // ü 用 u: 表示

        // 初始化不带声调的格式
        WITHOUT_TONE_FORMAT = new HanyuPinyinOutputFormat();
        WITHOUT_TONE_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        WITHOUT_TONE_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE); // 不带声调
        WITHOUT_TONE_FORMAT.setVCharType(HanyuPinyinVCharType.WITH_U_AND_COLON);
    }

    public static void main(String[] args) {
        // 1. 定义生成范围：常用汉字 Unicode 区间（0x4E00-0x9FFF）
        int startUnicode = 0x4E00;
        int endUnicode = 0x9FFF;
        // 2. 定义输出文件路径（默认生成在项目根目录，可修改为桌面路径如 "C:/Users/XXX/Desktop/pinyin_database.json"）
        String outputPath = "pinyin_database.json";

        System.out.println("=== 开始生成拼音库 ===");
        System.out.println("生成范围：Unicode " + Integer.toHexString(startUnicode) + " - " + Integer.toHexString(endUnicode));
        System.out.println("输出路径：" + new File(outputPath).getAbsolutePath());
        System.out.println("正在处理...（约 1-2 分钟，请稍候）");

        // 存储最终的拼音数据：key=汉字，value=拼音信息（带声调/不带声调）
        Map<String, PinyinInfo> pinyinDatabase = new LinkedHashMap<>();
        int successCount = 0; // 成功获取拼音的汉字数量
        int totalCount = endUnicode - startUnicode + 1; // 总汉字数量

        // 遍历所有汉字 Unicode
        for (int unicode = startUnicode; unicode <= endUnicode; unicode++) {
            char chineseChar = (char) unicode;
            String charStr = String.valueOf(chineseChar);

            try {
                // 3. 获取该汉字的所有拼音（去重，避免多音字重复）
                Set<String> withTonePinyins = getUniquePinyins(charStr, WITH_TONE_FORMAT);
                Set<String> withoutTonePinyins = getUniquePinyins(charStr, WITHOUT_TONE_FORMAT);

                // 4. 过滤无效拼音（确保拼音不为空）
                if (!withTonePinyins.isEmpty() && !withoutTonePinyins.isEmpty()) {
                    // 转换为有序列表（保持一致性）
                    List<String> withToneList = new ArrayList<>(withTonePinyins);
                    List<String> withoutToneList = new ArrayList<>(withoutTonePinyins);

                    // 存储到数据库
                    pinyinDatabase.put(charStr, new PinyinInfo(withToneList, withoutToneList));
                    successCount++;
                }

                // 打印进度（每处理 1000 个汉字输出一次）
                if (unicode % 1000 == 0) {
                    System.out.printf("进度：%d/%d（成功：%d）\n", unicode - startUnicode + 1, totalCount, successCount);
                }

            } catch (Exception e) {
                // 忽略个别处理失败的汉字（不影响整体）
                System.err.println("处理汉字 " + charStr + " 时出错：" + e.getMessage());
            }
        }

        // 5. 将数据转换为格式化的 JSON 并写入文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting() // 格式化输出（便于阅读）
                    .disableHtmlEscaping() // 避免中文/特殊字符转义（如 ü 不转义为 \u00fc）
                    .create();
            gson.toJson(pinyinDatabase, writer);

            System.out.println("=== 生成完成 ===");
            System.out.println("总处理汉字：" + totalCount);
            System.out.println("成功生成拼音：" + successCount);
            System.out.println("文件大小：约 " + new File(outputPath).length() / 1024 + " KB");
            System.out.println("文件路径：" + new File(outputPath).getAbsolutePath());

        } catch (IOException e) {
            System.err.println("写入文件失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取汉字的唯一拼音（去重）
     * @param chineseChar 单个汉字
     * @param format 拼音输出格式
     * @return 去重后的拼音集合
     */
    private static Set<String> getUniquePinyins(String chineseChar, HanyuPinyinOutputFormat format) throws BadHanyuPinyinOutputFormatCombination {
        Set<String> uniquePinyins = new LinkedHashSet<>();
        // 获取该汉字的所有拼音（多音字会返回多个）
        String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(chineseChar.charAt(0), format);

        if (pinyinArray != null && pinyinArray.length > 0) {
            // 去重（避免同一拼音重复出现）
            uniquePinyins.addAll(Arrays.asList(pinyinArray));
        }
        return uniquePinyins;
    }

    /**
     * 拼音信息实体类（用于 JSON 序列化）
     * 包含：带声调的拼音列表、不带声调的拼音列表
     */
    static class PinyinInfo {
        private List<String> withTone; // 带声调（如 ["zhōng", "zhòng"]）
        private List<String> withoutTone; // 不带声调（如 ["zhong", "zhong"]）

        public PinyinInfo(List<String> withTone, List<String> withoutTone) {
            this.withTone = withTone;
            this.withoutTone = withoutTone;
        }

        // Getter（Gson 序列化需要）
        public List<String> getWithTone() {
            return withTone;
        }

        public List<String> getWithoutTone() {
            return withoutTone;
        }
    }
}
