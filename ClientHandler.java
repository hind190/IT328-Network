// تحديد الحزمة (package) - نفس حزمة Server
package server;

// مكتبات الإدخال/الإخراج للقراءة والكتابة عبر الشبكة
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
// مكتبة Socket لتمثيل الاتصال
import java.net.Socket;

// كلاس ClientHandler يرث من Thread، لكي يعمل كل عميل في خيط مستقل
public class ClientHandler extends Thread {
    
    // السوكيت الخاص بهذا العميل (يمثل الاتصال بين السيرفر والعميل)
    private Socket socket;
    
    // لقراءة النصوص المرسلة من العميل
    private BufferedReader in;
    
    // لإرسال النصوص إلى العميل (true = auto-flush يرسل فوراً)
    private PrintWriter out;
    
    // اسم المستخدم لهذا العميل (يُستخرج من رسالة CONNECT)
    private String username;
    
    // مرجع إلى كائن السيرفر الرئيسي، لكي نستدعي دواله (addClient, broadcastPlayerList, ...)
    private Server server;

    // المُنشئ (Constructor) يُنشأ عندما يتصل عميل جديد
    // يستقبل السوكيت الخاص بالعميل، ومرجع السيرفر
    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;   // حفظ السوكيت
        this.server = server;   // حفظ مرجع السيرفر
        
        try {
            // تهيئة BufferedReader: تقوم بتحويل InputStream من السوكيت إلى نص يمكن قراءته سطراً بسطر
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // تهيئة PrintWriter: لإرسال النصوص إلى العميل (auto-flush = true يعني يرسل فوراً عند println)
            out = new PrintWriter(socket.getOutputStream(), true);
            
        } catch (IOException e) {
            // إذا حدث خطأ أثناء تهيئة الإدخال/الإخراج، اطبع الخطأ
            e.printStackTrace();
        }
    }

    // دالة getter لإرجاع اسم المستخدم (يستخدمها السيرفر عند بناء قائمة اللاعبين)
    public String getUsername() {
        return username;
    }
    
    // دالة لإرسال رسالة نصية إلى هذا العميل فقط
    public void sendMessage(String message) {
        out.println(message);   // println ترسل النص وتضيف سطر جديد في النهاية
    }

    // هذه الدالة تُنفَّذ تلقائياً عند استدعاء start() على كائن ClientHandler
    @Override
    public void run() {
        try {
            // ---------- الجزء الأول: استقبال رسالة CONNECT ----------
            // قراءة السطر الأول الذي يرسله العميل (نتوقع أنه بصيغة "CONNECT:username")
            String firstLine = in.readLine();
            
            // التحقق: هل السطر الأول يبدأ بـ "CONNECT:" ؟
            if (firstLine != null && firstLine.startsWith("CONNECT:")) {
                // استخراج اسم المستخدم: نأخذ الجزء بعد النقطتين (من الخانة 8 إلى النهاية)
                // مثال: "CONNECT:Ahmed" → "Ahmed"
                this.username = firstLine.substring(8);
                System.out.println("User '" + username + "' connected.");
                
                // ---------- الجزء الثاني: إضافة اللاعب إلى قائمة السيرفر ----------
                // استدعاء دالة addClient من السيرفر (synchronized لتجنب التعارض)
                server.addClient(this);
                
                // ---------- الجزء الثالث: إرسال قائمة اللاعبين المحدثة للجميع ----------
                // السيرفر سيبث رسالة PLAYER_LIST تحتوي على أسماء جميع المتصلين
                server.broadcastPlayerList();
                
                // بعد ذلك، نرسل رسالة تأكيد خاصة لهذا العميل (اختياري ولكن مفيد)
                sendMessage("CONNECT_OK:" + username);
                
            } else {
                // إذا لم يرسل العميل CONTRACT صالحة، أغلق الاتصال فوراً
                System.out.println("Invalid connection attempt, closing.");
                socket.close();
                return;   // نخرج من الدالة run() ولا نكمل
            }
            
            // ---------- الجزء الرابع: الاستماع للرسائل القادمة من هذا العميل (للمراحل القادمة) ----------
            // هذه الحلقة ستستمر في القراءة طالما العميل متصل
            // حاليًا سنطبع الرسائل فقط، لكن لاحقًا سنضيف منطق PLAY_REQUEST وغيره
            String message;
            while ((message = in.readLine()) != null) {
                // طباعة الرسالة المستلمة في طرفية السيرفر للمتابعة
                System.out.println("Received from " + username + ": " + message);
                
                // هنا مستقبلاً ستُضاف معالجة رسائل مثل:
                // PLAY_REQUEST (الشخص 3), LEAVE (الشخص 2/3), ANSWER (حسب فكرة اللعبة)
                // حالياً يمكن إرسال رد echo بسيط (للتأكد من الاتصال)
                sendMessage("ECHO: " + message);
            }
            
        } catch (IOException e) {
            // إذا حدث خطأ في القراءة (مثل انقطاع العميل فجأة)
            System.out.println("Connection lost with client: " + username);
        } finally {
            // ---------- التنظيف عند انقطاع العميل أو خروجه ----------
            // إذا كان الاسم قد سُجل (ليس null)، نزيل العميل من قائمة السيرفر
            if (username != null) {
                server.removeClient(this);
                // نُرسل قائمة اللاعبين المحدثة للجميع بعد الإزالة
                server.broadcastPlayerList();
                System.out.println("User '" + username + "' removed from list.");
            }
            
            // إغلاق جميع الموارد (المقابس والقارئات والكتابات) لتجنب تسرب الذاكرة
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}