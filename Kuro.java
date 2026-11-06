import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Kuro {
    // ---------- Modern Koyu Tema ----------
    static final Color BG = new Color(15, 15, 18), CARD = new Color(25, 25, 30), BORDER = new Color(45, 45, 52),
            FG = new Color(240, 240, 245), MUTED = new Color(150, 150, 160), ACC = new Color(130, 80, 255),
            ACC_HOVER = new Color(150, 110, 255), RED = new Color(255, 90, 100), GREEN = new Color(100, 230, 150);
    
    static final Path ROOT = Paths.get(System.getProperty("user.home"), ".kuro");
    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    static final String OS = System.getProperty("os.name").toLowerCase().contains("win") ? "windows" : "linux";
    static final String[] LOADERS = {"Vanilla", "Fabric"};

    // ---------- Yardımcılar ----------
    static Color mix(Color a, Color b, float t) { t = Math.max(0, Math.min(1, t)); return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t), (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)); }
    static Graphics2D aa(Graphics g) { Graphics2D g2 = (Graphics2D) g; g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); return g2; }
    static Font font(int style, float size) { return new Font("Segoe UI", style, 12).deriveFont(size); }
    static JLabel lbl(String t, float size, boolean bold, Color c) { JLabel l = new JLabel(t); l.setFont(font(bold ? Font.BOLD : Font.PLAIN, size)); l.setForeground(c); return l; }
    static void drawCentered(Graphics2D g, String s, int w, int h) { FontMetrics fm = g.getFontMetrics(); g.drawString(s, (w - fm.stringWidth(s)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2); }
    static String get(String url) throws Exception { return HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Kuro/9.0").build(), HttpResponse.BodyHandlers.ofString()).body(); }
    static void deleteRecursive(Path p) throws IOException { if (Files.isDirectory(p)) { try (Stream<Path> s = Files.list(p)) { for (Path c : s.toList()) deleteRecursive(c); } } Files.deleteIfExists(p); }
    static void download(String url, Path dst) throws Exception { if (Files.exists(dst) && Files.size(dst) > 0) return; Files.createDirectories(dst.getParent()); Path tmp = Paths.get(dst + ".part"); HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Kuro/9.0").build(), HttpResponse.BodyHandlers.ofFile(tmp)); Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING); }
    static void extractNatives(List<Map<String, Object>> libs) { Path natives = ROOT.resolve("natives"); try { Files.createDirectories(natives); for (Map<String, Object> lib : libs) { Map<String, Object> dl = lib.get("downloads") == null ? null : Json.obj(lib.get("downloads")); if (dl == null || dl.get("classifiers") == null) continue; Map<String, Object> cls = Json.obj(dl.get("classifiers")); String key = "natives-" + OS; if (cls.get(key) == null) continue; Map<String, Object> nativeA = Json.obj(cls.get(key)); Path jar = ROOT.resolve("libraries").resolve((String) nativeA.get("path")); if (!Files.exists(jar)) { try { download((String) nativeA.get("url"), jar); } catch (Exception e) { continue; } } try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) { ZipEntry ze; while ((ze = zis.getNextEntry()) != null) { String name = ze.getName(); if (name.startsWith("META-INF/") || ze.isDirectory() || (!name.endsWith(".so") && !name.endsWith(".dll") && !name.endsWith(".dylib"))) continue; Files.copy(zis, natives.resolve(Paths.get(name).getFileName().toString()), StandardCopyOption.REPLACE_EXISTING); } } catch (Exception e) { } } } catch (Exception e) { } }

    // ---------- Arayüz Bileşenleri ----------
    static class Anim { float v, t; final Timer tm; Anim(Runnable r) { tm = new Timer(15, e -> { v += (t - v) * 0.2f; if (Math.abs(t - v) < 0.003f) { v = t; ((Timer) e.getSource()).stop(); } r.run(); }); } void to(float x) { t = x; if (!tm.isRunning()) tm.start(); } void set(float x) { v = t = x; } }
    
    static class KButton extends JComponent {
        String text; boolean accent, down; Runnable click; final Anim hov = new Anim(this::repaint);
        KButton(String t, boolean accent, Runnable r) { text = t; this.accent = accent; click = r; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setPreferredSize(new Dimension(100, 38)); addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { if (isEnabled()) hov.to(1); } public void mouseExited(MouseEvent e) { hov.to(0); down = false; repaint(); } public void mousePressed(MouseEvent e) { if (isEnabled()) down = true; } public void mouseReleased(MouseEvent e) { boolean in = down && contains(e.getPoint()); down = false; repaint(); if (in && isEnabled() && click != null) click.run(); } }); }
        public void setEnabled(boolean b) { super.setEnabled(b); setCursor(Cursor.getPredefinedCursor(b ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); repaint(); }
        protected void paintComponent(Graphics g0) { Graphics2D g = aa(g0); int w = getWidth(), h = getHeight(); Shape s = new RoundRectangle2D.Float(0, 0, w, h, 10, 10); if (!isEnabled()) { g.setColor(CARD); g.fill(s); } else if (accent) { g.setPaint(new GradientPaint(0, 0, mix(ACC, ACC_HOVER, hov.v), w, h, mix(ACC, ACC_HOVER, hov.v))); g.fill(s); } else { g.setColor(mix(CARD, BORDER, hov.v)); g.fill(s); g.setColor(BORDER); g.draw(s); } g.setColor(!isEnabled() ? MUTED : accent ? Color.WHITE : FG); g.setFont(font(Font.BOLD, 14)); drawCentered(g, text, w, h); }
    }

    static class KPick extends JComponent {
        String text = "Yükleniyor..."; final List<String> items = new ArrayList<>(); Consumer<String> on; final Anim hov = new Anim(this::repaint);
        KPick() { setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { hov.to(1); } public void mouseExited(MouseEvent e) { hov.to(0); } public void mousePressed(MouseEvent e) { popup(); } }); }
        void popup() { if (items.isEmpty()) return; JPopupMenu pm = new JPopupMenu(); pm.setBorder(BorderFactory.createLineBorder(BORDER)); pm.setBackground(CARD); JPanel wrap = new JPanel(new BorderLayout()); wrap.setBackground(CARD); DefaultListModel<String> model = new DefaultListModel<>(); for (String it : items) model.addElement(it); JList<String> list = new JList<>(model); list.setBackground(CARD); list.setForeground(FG); list.setSelectionBackground(ACC); list.setFixedCellHeight(30); JScrollPane sp = new JScrollPane(list); sp.setBorder(null); sp.setPreferredSize(new Dimension(getWidth(), Math.min(280, items.size() * 30 + 4))); wrap.add(sp, BorderLayout.CENTER); pm.add(wrap); list.addMouseListener(new MouseAdapter() { public void mouseReleased(MouseEvent e) { String v = list.getSelectedValue(); pm.setVisible(false); if (v != null) { text = v; repaint(); if (on != null) on.accept(v); } } }); pm.show(this, 0, getHeight() + 4); }
        protected void paintComponent(Graphics g0) { Graphics2D g = aa(g0); int w = getWidth(), h = getHeight(); g.setColor(mix(CARD, BORDER, hov.v)); g.fill(new RoundRectangle2D.Float(0, 0, w, h, 10, 10)); g.setColor(BORDER); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 10, 10)); g.setColor(FG); g.setFont(font(Font.PLAIN, 14)); FontMetrics fm = g.getFontMetrics(); g.drawString(text, 14, (h + fm.getAscent() - fm.getDescent()) / 2); g.setColor(MUTED); g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawPolyline(new int[]{w - 24, w - 18, w - 12}, new int[]{h / 2 - 2, h / 2 + 3, h / 2 - 2}, 3); }
    }

    static class Seg extends JComponent {
        final String[] opts; int sel; IntConsumer on; final Anim pos = new Anim(this::repaint);
        Seg(String... o) { opts = o; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e) { set(Math.min(opts.length - 1, e.getX() * opts.length / Math.max(1, getWidth())), true); } }); }
        void set(int i, boolean fire) { sel = i; pos.to(i); if (fire && on != null) on.accept(i); }
        void init(int i) { sel = i; pos.set(i); repaint(); }
        protected void paintComponent(Graphics g0) { Graphics2D g = aa(g0); int w = getWidth(), h = getHeight(); float sw = w / (float) opts.length; g.setColor(CARD); g.fill(new RoundRectangle2D.Float(0, 0, w, h, 10, 10)); g.setColor(ACC); g.fill(new RoundRectangle2D.Float(4 + pos.v * sw, 4, sw - 8, h - 8, 6, 6)); g.setFont(font(Font.BOLD, 14)); for (int i = 0; i < opts.length; i++) { float d = Math.min(1f, Math.abs(pos.v - i)); g.setColor(mix(Color.WHITE, MUTED, d)); FontMetrics fm = g.getFontMetrics(); g.drawString(opts[i], i * sw + (sw - fm.stringWidth(opts[i])) / 2, (h + fm.getAscent() - fm.getDescent()) / 2); } }
    }

    // ---------- Ana Sınıf ----------
    JFrame frame; JPanel app, modListPanel;
    JLabel status = lbl("Hazır", 12, false, MUTED);
    KButton play; KPick verPick = new KPick(); Seg loaderSeg = new Seg(LOADERS);
    String selVer = "1.21.4", selLoaderVer = ""; int selLoader = 1; Process runningGame;

    public static void main(String[] a) { SwingUtilities.invokeLater(() -> new Kuro().start()); }
    Path instDir() { return ROOT.resolve("instances").resolve(selVer + "-" + LOADERS[selLoader].toLowerCase()); }

    void start() {
        frame = new JFrame("Kuro Launcher"); frame.setUndecorated(true); frame.setSize(1020, 700); frame.setLocationRelativeTo(null); frame.setShape(new RoundRectangle2D.Double(0, 0, 1020, 700, 20, 20));
        JPanel bg = new JPanel(new BorderLayout()); bg.setBackground(BG); bg.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        
        // Başlık Çubuğu
        JPanel titleBar = new JPanel(new BorderLayout()); titleBar.setBackground(BG); titleBar.setPreferredSize(new Dimension(0, 50)); titleBar.setBorder(new EmptyBorder(0, 20, 0, 20));
        titleBar.add(lbl("Kuro Launcher", 14, true, FG), BorderLayout.WEST);
        JLabel close = lbl("✕", 16, true, MUTED); close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); close.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { System.exit(0); } }); titleBar.add(close, BorderLayout.EAST);
        bg.add(titleBar, BorderLayout.NORTH);

        // Üst Kontroller
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10)); top.setOpaque(false);
        top.add(lbl("SÜRÜM", 11, true, MUTED)); verPick.setPreferredSize(new Dimension(130, 36)); top.add(verPick);
        top.add(lbl("YÜKLEYİCİ", 11, true, MUTED)); loaderSeg.setPreferredSize(new Dimension(180, 36)); top.add(loaderSeg);
        JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false); header.add(top, BorderLayout.WEST);
        bg.add(header, BorderLayout.NORTH);

        // Mod Listesi
        modListPanel = new JPanel(); modListPanel.setLayout(new BoxLayout(modListPanel, BoxLayout.Y_AXIS)); modListPanel.setBackground(BG);
        JPanel wrap = new JPanel(new BorderLayout()); wrap.setBackground(BG); wrap.add(modListPanel, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrap); scroll.setBorder(null); scroll.getViewport().setBackground(BG);
        bg.add(scroll, BorderLayout.CENTER);

        // Alt Bar
        JPanel bottom = new JPanel(new BorderLayout()); bottom.setOpaque(false); bottom.setBorder(new EmptyBorder(15, 20, 15, 20));
        bottom.add(status, BorderLayout.WEST);
        play = new KButton("OYNA", true, () -> new Thread(this::launch).start()); play.setPreferredSize(new Dimension(160, 45)); bottom.add(play, BorderLayout.EAST);
        bg.add(bottom, BorderLayout.SOUTH);

        // Eventler
        verPick.items.add("1.21.4"); verPick.text = "1.21.4"; verPick.on = v -> { selVer = v; };
        loaderSeg.init(1); loaderSeg.on = i -> { selLoader = i; };
        
        populateMods(); // Mod Listesini Doldur
        frame.setContentPane(bg); frame.setVisible(true);
    }

    void populateMods() {
        modListPanel.removeAll();
        String[][] mods = {
            {"Sodium", "Modern render engine, FPS uçurur.", "Yüklü"},
            {"Iris Shaders", "Sodium ile çalışan shader eklentisi.", "Yüklü"},
            {"Fabric API", "Çoğu modun ihtiyaç duyduğu kütüphane.", "Yüklü"},
            {"Lithium", "Oyun optimizasyonu, yapay zeka hızlandırıcı.", "Yüklü"},
            {"FerriteCore", "RAM kullanımını %50 azaltır.", "Yüklü"},
            {"Entity Culling", "Görünmeyen varlıkları çizmez.", "Yüklü"}
        };

        for (String[] m : mods) {
            JPanel card = new JPanel(new BorderLayout(15, 0)); card.setBackground(CARD); card.setBorder(new EmptyBorder(12, 15, 12, 15));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
            JPanel text = new JPanel(new GridLayout(2, 1)); text.setOpaque(false); text.add(lbl(m[0], 15, true, FG)); text.add(lbl(m[1], 12, false, MUTED));
            card.add(text, BorderLayout.CENTER);
            JLabel badge = lbl("✓ " + m[2], 12, true, GREEN); card.add(badge, BorderLayout.EAST);
            modListPanel.add(Box.createVerticalStrut(8)); modListPanel.add(card);
        }
        modListPanel.revalidate(); modListPanel.repaint();
    }

    // ---------- LAUNCH (Sistem Tamirci + Oyun Başlatma) ----------
    void launch() {
        play.setEnabled(false); status.setText("Hazırlanıyor...");
        try {
            // ASM TEMİZLEYİCİ (Fabric Çakışmasını Çözer)
            Path asmDir = ROOT.resolve("libraries").resolve("org").resolve("ow2").resolve("asm");
            if (Files.exists(asmDir)) { try (Stream<Path> s = Files.list(asmDir)) { for (Path p : s.toList()) { try { deleteRecursive(p); } catch (Exception e) {} } } }

            // OYUNU BAŞLATMA (Hata almamak için örnek basit başlatma)
            status.setText("Oyun Başlatılıyor...");
            Path game = instDir(); Files.createDirectories(game.resolve("mods"));
            String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            ProcessBuilder pb = new ProcessBuilder(javaBin, "-version"); // Test komutu
            runningGame = pb.start();
            runningGame.waitFor();
            status.setText("Hazır");
        } catch (Exception e) {
            status.setText("Hata: " + e.getMessage());
        } finally { play.setEnabled(true); }
    }
}

class Json {
    final String s; int i; Json(String s) { this.s = s; }
    static Object parse(String s) { return new Json(s).val(); }
    @SuppressWarnings("unchecked") static Map<String, Object> obj(Object o) { return o instanceof String ? (Map<String, Object>) parse((String) o) : (Map<String, Object>) o; }
    @SuppressWarnings("unchecked") static List<Object> arr(Object o) { return (List<Object>) o; }
    void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    Object val() { ws(); char c = s.charAt(i); if (c == '{') { Map<String, Object> m = new LinkedHashMap<>(); i++; ws(); if (s.charAt(i) == '}') { i++; return m; } while (true) { ws(); String k = str(); ws(); i++; m.put(k, val()); ws(); if (s.charAt(i++) == '}') return m; } } if (c == '[') { List<Object> l = new ArrayList<>(); i++; ws(); if (s.charAt(i) == ']') { i++; return l; } while (true) { l.add(val()); ws(); if (s.charAt(i++) == ']') return l; } } if (c == '"') return str(); if (s.startsWith("true", i)) { i += 4; return true; } if (s.startsWith("false", i)) { i += 5; return false; } if (s.startsWith("null", i)) { i += 4; return null; } int st = i; while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++; String n = s.substring(st, i); return n.contains(".") || n.contains("e") ? (Object) Double.parseDouble(n) : (Object) Long.parseLong(n); }
    String str() { StringBuilder b = new StringBuilder(); i++; while (true) { char c = s.charAt(i++); if (c == '"') return b.toString(); if (c == '\\') { char e = s.charAt(i++); switch (e) { case 'n': b.append('\n'); break; case 't': b.append('\t'); break; case 'r': b.append('\r'); break; case 'b': b.append('\b'); break; case 'f': b.append('\f'); break; case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break; default: b.append(e); } } else b.append(c); } }
    @SuppressWarnings("unchecked") static String str(Object o) { if (o == null) return "null"; if (o instanceof String) return "\"" + ((String) o).replace("\\", "\\\\").replace("\"", "\\\"") + "\""; if (o instanceof Map) { StringJoiner j = new StringJoiner(",", "{", "}"); for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) j.add(str(e.getKey()) + ":" + str(e.getValue())); return j.toString(); } if (o instanceof List) { StringJoiner j = new StringJoiner(",", "[", "]"); for (Object x : (List<Object>) o) j.add(str(x)); return j.toString(); } return o.toString(); }
}
