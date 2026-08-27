import 'package:flutter/services.dart';

class SmsNtfySettings {
  const SmsNtfySettings({
    required this.server,
    required this.topic,
    required this.replyTopic,
    this.username = '',
    this.password = '',
    this.priority = 4,
    this.enabled = true,
  });

  final String server;
  final String topic;
  final String replyTopic;
  final String username;
  final String password;
  final int priority;
  final bool enabled;

  Map<String, Object> toMap() => <String, Object>{
    'server': server.trim().replaceFirst(RegExp(r'/+$'), ''),
    'topic': topic.trim().replaceFirst(RegExp(r'^/+'), ''),
    'replyTopic': replyTopic.trim().replaceFirst(RegExp(r'^/+'), ''),
    'username': username,
    'password': password,
    'priority': priority,
    'enabled': enabled,
  };
}

class SmsNtfyPlatform {
  static const MethodChannel _channel = MethodChannel('com.smsntfy.flutter/service');
  static const EventChannel events = EventChannel('com.smsntfy.flutter/events');

  static Future<bool> requestPermissions() async =>
      await _channel.invokeMethod<bool>('requestPermissions') ?? false;

  static Future<void> saveSettings(SmsNtfySettings settings) =>
      _channel.invokeMethod<void>('saveSettings', settings.toMap());

  static Future<Map<String, dynamic>> getSettings() async =>
      Map<String, dynamic>.from(await _channel.invokeMapMethod<String, dynamic>('getSettings') ?? {});

  static Future<bool> start() async => await _channel.invokeMethod<bool>('startService') ?? false;
  static Future<bool> stop() async => await _channel.invokeMethod<bool>('stopService') ?? false;
  static Future<bool> sendTest() async => await _channel.invokeMethod<bool>('sendTest') ?? false;
}
