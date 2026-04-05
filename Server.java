// تحديد الحزمة (package) التي يوجد فيها هذا الملف
package server;

// استيراد المكتبات اللازمة للتعامل مع الإدخال/الإخراج
import java.io.IOException;
// استيراد مكتبة ServerSocket لإنشاء سيرفر يستمع على منفذ معين
import java.net.ServerSocket;
// استيراد مكتبة Socket لتمثيل الاتصال بين السيرفر والعميل
import java.net.Socket;
// استيراد ArrayList لتخزين قائمة العملاء
import java.util.ArrayList;
// استيراد List للتعامل مع القائمة بشكل عام
import java.util.List;

// تعريف كلاس Server (السيرفر)
public class Server {
    
    // كائن ServerSocket لفتح منفذ والاستماع على الاتصالات الواردة
    private ServerSocket serverSocket;
    
    // قائمة (ArrayList) لتخزين كائنات ClientHandler لكل عميل متصل
    // <ClientHandler> تعني أن القائمة تحتوي فقط على أشياء من نوع ClientHandler
    private List<ClientHandler> clients = new ArrayList<>();
    
    // متغير boolean يتحكم في استمرارية تشغيل السيرفر (true = شغال، false = أوقف)
    private boolean running = true;

    // دالة start: تبدأ تشغيل السيرفر على منفذ محدد (port)
    public void start(int port) {
        try {
            // إنشاء كائن ServerSocket وربطه بالمنفذ المطلوب
            serverSocket = new ServerSocket(port);
            // طباعة رسالة في الطرفية تفيد بأن السيرفر يعمل
            System.out.println("Server is running on port " + port);

            // حلقة لا نهائية (طالما running = true) لاستقبال عملاء جدد
            while (running) {
                // accept() تنتظر حتى يتصل عميل جديد، ثم ترجع كائن Socket خاص به
                Socket socket = serverSocket.accept();
                // طباعة عنوان IP الخاص بالعميل المتصل
                System.out.println("New client connected: " + socket.getInetAddress());

                // إنشاء كائن ClientHandler جديد، وتمرير السوكيت (socket) ومرجع السيرفر (this)
                // this يعني الكائن الحالي من Server
                ClientHandler clientHandler = new ClientHandler(socket, this);
                
                // بدء Thread الخاص بهذا العميل (يشتغل بشكل مستقل)
                clientHandler.start();
            }

        } catch (IOException e) {
            // في حال حدوث خطأ في الإدخال/الإخراج (مثل المنفذ مشغول)، اطبع تفاصيل الخطأ
            e.printStackTrace();
        }
    }

    // دالة لإضافة عميل إلى قائمة العملاء
    // synchronized تعني أن دالة واحدة فقط تستطيع تنفيذها في نفس الوقت (لتجنب التعارض بين الخيوط)
    public synchronized void addClient(ClientHandler client) {
        clients.add(client);
    }

    // دالة لحذف عميل من قائمة العملاء (عندما يغادر أو ينقطع)
    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    // دالة تبث (ترسل) قائمة بأسماء جميع اللاعبين إلى جميع العملاء المتصلين
    public synchronized void broadcastPlayerList() {
        // إذا كانت القائمة فارغة (لا يوجد عملاء)، لا تفعل شيئًا
        if (clients.isEmpty()) return;
        
        // StringBuilder لبناء النص المرسل (أسرع من String العادي)
        StringBuilder sb = new StringBuilder("PLAYER_LIST:");
        
        // حلقة لتمر على جميع العملاء في القائمة
        for (int i = 0; i < clients.size(); i++) {
            // إذا لم يكن هذا أول عنصر، أضف فاصلة قبل الاسم
            if (i > 0) sb.append(",");
            // أضف اسم المستخدم (بنستخدم دالة getUsername() اللي راح تكتبها في ClientHandler)
            sb.append(clients.get(i).getUsername());
        }
        
        // تحويل StringBuilder إلى String عادية
        String msg = sb.toString();
        
        // حلقة لإرسال هذه الرسالة (القائمة) لكل عميل على حدة
        for (ClientHandler c : clients) {
            c.sendMessage(msg); // sendMessage() هي دالة راح تكتبها في ClientHandler
        }
    }

    // الدالة الرئيسية (main) التي تُشغَّل أولاً عند بدء البرنامج
    public static void main(String[] args) {
        // إنشاء كائن جديد من Server ثم استدعاء دالة start على المنفذ 1234
        new Server().start(1234);
    }
}