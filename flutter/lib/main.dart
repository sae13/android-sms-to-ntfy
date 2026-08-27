import 'package:flutter/material.dart';
import 'sms_ntfy_platform.dart';

void main() => runApp(const SmsNtfyApp());

class SmsNtfyApp extends StatelessWidget {
  const SmsNtfyApp({super.key});
  @override Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: ThemeData(colorSchemeSeed: const Color(0xff4f46e5), useMaterial3: true),
    home: const HomePage(),
  );
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final server = TextEditingController(text: 'https://ntfy.sh');
  final topic = TextEditingController(text: 'sms-alerts');
  final replyTopic = TextEditingController(text: 'sms-replies');
  final username = TextEditingController();
  final password = TextEditingController();
  bool running = false;
  bool busy = false;
  String status = 'Ready';

  @override void initState() { super.initState(); _load(); }
  Future<void> _load() async {
    try {
      final s = await SmsNtfyPlatform.getSettings();
      server.text = s['server'] as String? ?? server.text;
      topic.text = s['topic'] as String? ?? topic.text;
      replyTopic.text = s['replyTopic'] as String? ?? replyTopic.text;
      username.text = s['username'] as String? ?? '';
      password.text = s['password'] as String? ?? '';
      setState(() => running = s['running'] as bool? ?? false);
    } catch (_) { setState(() => status = 'Android platform is required'); }
  }
  SmsNtfySettings get settings => SmsNtfySettings(server: server.text, topic: topic.text,
      replyTopic: replyTopic.text, username: username.text, password: password.text);
  Future<void> _run(Future<void> Function() action, String success) async {
    setState(() { busy = true; status = 'Working…'; });
    try { await action(); setState(() => status = success); }
    on PlatformException catch (e) { setState(() => status = e.message ?? e.code); }
    finally { setState(() => busy = false); }
  }
  @override Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('SMS → ntfy')),
    body: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 640), child:
      ListView(padding: const EdgeInsets.all(24), children: [
        Card(child: Padding(padding: const EdgeInsets.all(20), child: Column(children: [
          TextField(controller: server, decoration: const InputDecoration(labelText: 'ntfy server')),
          TextField(controller: topic, decoration: const InputDecoration(labelText: 'Notification topic')),
          TextField(controller: replyTopic, decoration: const InputDecoration(labelText: 'Reply topic')),
          TextField(controller: username, decoration: const InputDecoration(labelText: 'Username (optional)')),
          TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: 'Password (optional)')),
          const SizedBox(height: 20),
          Row(children: [
            Expanded(child: FilledButton.icon(onPressed: busy ? null : () => _run(() async {
              await SmsNtfyPlatform.requestPermissions(); await SmsNtfyPlatform.saveSettings(settings);
              running = await SmsNtfyPlatform.start(); setState(() {});
            }, 'Forwarding is active'), icon: const Icon(Icons.play_arrow), label: const Text('Save & start'))),
            const SizedBox(width: 12),
            OutlinedButton(onPressed: busy ? null : () => _run(() async { running = !(await SmsNtfyPlatform.stop()); setState(() {}); }, 'Stopped'), child: const Text('Stop')),
          ]),
          const SizedBox(height: 12),
          TextButton(onPressed: busy ? null : () => _run(() async { await SmsNtfyPlatform.saveSettings(settings); final ok = await SmsNtfyPlatform.sendTest(); if (!ok) throw const PlatformException(code: 'send_failed', message: 'ntfy rejected the test'); }, 'Test notification sent'), child: const Text('Send test notification')),
        ]))),
        const SizedBox(height: 16),
        ListTile(leading: Icon(running ? Icons.cloud_done : Icons.cloud_off, color: running ? Colors.green : Colors.grey), title: Text(status), subtitle: const Text('Incoming SMS are forwarded by an Android foreground service.')),
      ]))),
  );
}
