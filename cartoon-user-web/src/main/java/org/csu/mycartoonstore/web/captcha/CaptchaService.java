package org.csu.mycartoonstore.web.captcha; // 图形验证码：生成 4 位字符并画成 PNG。

import org.springframework.stereotype.Service; // 业务组件。

import javax.imageio.ImageIO; // 输出 PNG。
import java.awt.Color; // 配色。
import java.awt.Font; // 字体。
import java.awt.Graphics2D; // 画布。
import java.awt.RenderingHints; // 抗锯齿。
import java.awt.image.BufferedImage; // 内存图片。
import java.io.ByteArrayOutputStream; // 输出到字节数组。
import java.io.IOException; // 编码异常。
import java.security.SecureRandom; // 随机源。

// M10-04：依据指导书实验内容一「用户模块：注册、登录、验证码」。
// 负责：生成 4 位验证码、画成 120×40 的 PNG；字符集去掉易混的 0/O/1/I/l。
// 不做：短信/邮件验证码、第三方图形服务、字体文件加载（用 JDK 逻辑字体）。
@Service // 无状态，可单例。
public class CaptchaService {

    /** 会话里存验证码的键：与控制器、测试共用。 */
    public static final String SESSION_KEY = "captchaCode"; // 只放会话。

    /** 表单参数名。 */
    public static final String PARAM_NAME = "captcha"; // 与模板一致。

    /** 验证码长度：4 位在可读性与暴力成本之间够用。 */
    public static final int LENGTH = 4; // 定长。

    /** 字符集：去掉 0/O/1/I/l 这些易混字符。 */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 32 个字符。

    private final SecureRandom random = new SecureRandom(); // 随机源。

    /** 生成一个新验证码（大写，便于忽略大小写比对）。 */
    public String newCode() {
        StringBuilder builder = new StringBuilder(LENGTH); // 定长。
        for (int i = 0; i < LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length()))); // 等概率取字符。
        }
        return builder.toString(); // 交给调用方写会话。
    }

    /** 把验证码画成 PNG：浅底、噪线、轻微旋转，机器识别成本低但足够挡脚本。 */
    public byte[] png(String code) {
        BufferedImage image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB); // 120×40 三通道。
        Graphics2D graphics = image.createGraphics(); // 拿到画布。
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // 抗锯齿。
            graphics.setColor(new Color(0xF7, 0xF1, 0xE3)); // 与站点底色一致。
            graphics.fillRect(0, 0, 120, 40); // 铺底。
            for (int i = 0; i < 6; i++) { // 噪线。
                graphics.setColor(noise()); // 随机浅色。
                graphics.drawLine(random.nextInt(120), random.nextInt(40),
                        random.nextInt(120), random.nextInt(40)); // 两点连线。
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26)); // 逻辑字体，无需字体文件。
            for (int i = 0; i < code.length(); i++) {
                graphics.setColor(i % 2 == 0 ? new Color(0x1E, 0x1E, 0x1E) : new Color(0xB3, 0x26, 0x1E)); // 深浅交替。
                int x = 12 + i * 26; // 字符间距。
                int y = 29 + random.nextInt(4) - 2; // 上下抖动。
                graphics.drawString(String.valueOf(code.charAt(i)), x, y); // 逐字画。
            }
        } finally {
            graphics.dispose(); // 释放画布。
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) { // 内存输出。
            ImageIO.write(image, "png", out); // 编码。
            return out.toByteArray(); // 交给控制器。
        } catch (IOException e) { // 理论上不会发生。
            throw new IllegalStateException("验证码图片生成失败", e); // 交给统一 500。
        }
    }

    /** 噪声颜色：低饱和，避免盖住字符。 */
    private Color noise() {
        return new Color(150 + random.nextInt(80), 140 + random.nextInt(80), 120 + random.nextInt(80)); // 浅灰棕。
    }
}
