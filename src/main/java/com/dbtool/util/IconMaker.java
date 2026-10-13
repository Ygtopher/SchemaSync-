package com.dbtool.util;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class IconMaker {

    public static byte[] createIcoFromImages(List<BufferedImage> images) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int count = images.size();

        // 6-byte header
        ByteBuffer header = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0); // reserved
        header.putShort((short) 1); // icon type
        header.putShort((short) count);
        baos.write(header.array());

        List<byte[]> pngBytesList = new ArrayList<>();
        for (BufferedImage img : images) {
            ByteArrayOutputStream pngBaos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", pngBaos);
            pngBytesList.add(pngBaos.toByteArray());
        }

        int offset = 6 + (count * 16);
        for (int i = 0; i < count; i++) {
            BufferedImage img = images.get(i);
            byte[] pngData = pngBytesList.get(i);
            int w = img.getWidth() >= 256 ? 0 : img.getWidth();
            int h = img.getHeight() >= 256 ? 0 : img.getHeight();

            ByteBuffer entry = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            entry.put((byte) w);
            entry.put((byte) h);
            entry.put((byte) 0); // palette
            entry.put((byte) 0); // reserved
            entry.putShort((short) 1); // color planes
            entry.putShort((short) 32); // bpp
            entry.putInt(pngData.length);
            entry.putInt(offset);
            baos.write(entry.array());

            offset += pngData.length;
        }

        for (byte[] pngData : pngBytesList) {
            baos.write(pngData);
        }

        return baos.toByteArray();
    }

    public static BufferedImage resize(BufferedImage src, int width, int height) {
        BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dest.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, width, height, null);
        g2.dispose();
        return dest;
    }

    public static void convertImageToAppIcons(File sourceImgFile, File destDir) throws IOException {
        BufferedImage src = ImageIO.read(sourceImgFile);
        if (src == null) {
            throw new IllegalArgumentException("Cannot read source image: " + sourceImgFile);
        }

        int[] sizes = {256, 128, 64, 48, 32, 16};
        List<BufferedImage> icoImages = new ArrayList<>();
        for (int s : sizes) {
            icoImages.add(resize(src, s, s));
        }

        // Save app.ico
        byte[] icoData = createIcoFromImages(icoImages);
        File icoFile = new File(destDir, "app.ico");
        try (FileOutputStream fos = new FileOutputStream(icoFile)) {
            fos.write(icoData);
        }

        // Save pngs
        ImageIO.write(resize(src, 512, 512), "PNG", new File(destDir, "app-icon.png"));
        ImageIO.write(resize(src, 64, 64), "PNG", new File(destDir, "app-icon-64.png"));
        ImageIO.write(resize(src, 32, 32), "PNG", new File(destDir, "app-icon-32.png"));
        ImageIO.write(resize(src, 16, 16), "PNG", new File(destDir, "app-icon-16.png"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: IconMaker <sourceImage> <destDir>");
            System.exit(1);
        }
        File srcFile = new File(args[0]);
        File destDir = new File(args[1]);
        if (!destDir.exists()) destDir.mkdirs();
        convertImageToAppIcons(srcFile, destDir);
        System.out.println("Successfully generated app.ico and app-icon PNGs in: " + destDir.getAbsolutePath());
    }
}
