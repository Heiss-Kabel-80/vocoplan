import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:speech_to_text/speech_to_text.dart' as stt;
import 'package:speech_to_text/speech_recognition_result.dart';
import 'package:device_calendar/device_calendar.dart';
import 'package:intl/intl.dart';
import 'package:intl/date_symbol_data_local.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('de_DE', null);
  runApp(const VocoPlanApp());
}

class VocoPlanApp extends StatefulWidget {
  const VocoPlanApp({super.key});

  @override
  State<VocoPlanApp> createState() => _VocoPlanAppState();
}

class _VocoPlanAppState extends State<VocoPlanApp> {
  ThemeMode _themeMode = ThemeMode.dark;

  void _toggleTheme() {
    setState(() {
      _themeMode = _themeMode == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark;
    });
  }

  @override
  Widget build(BuildContext context) {
    const primaryBlue = Color(0xFF2563EB);
    const indigoAccent = Color(0xFF60A5FA);

    final darkTheme = ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: const Color(0xFF0B1320),
      colorScheme: const ColorScheme.dark(
        primary: indigoAccent,
        secondary: Color(0xFF818CF8),
        surface: Color(0xFF131D2E),
        surfaceContainerHighest: Color(0xFF1E2B40),
        onSurface: Color(0xFFF1F5F9),
        outline: Color(0xFF2E3E58),
      ),
    );

    final lightTheme = ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      scaffoldBackgroundColor: const Color(0xFFF5F7FB),
      colorScheme: const ColorScheme.light(
        primary: primaryBlue,
        secondary: Color(0xFF4338CA),
        surface: Colors.white,
        surfaceContainerHighest: Color(0xFFE8EEF8),
        onSurface: Color(0xFF0F172A),
        outline: Color(0xFFCBD5E1),
      ),
    );

    return MaterialApp(
      title: 'VocoPlan',
      debugShowCheckedModeBanner: false,
      theme: lightTheme,
      darkTheme: darkTheme,
      themeMode: _themeMode,
      home: HomeScreen(onToggleTheme: _toggleTheme, isDarkMode: _themeMode == ThemeMode.dark),
    );
  }
}

class ParsedEvent {
  String title;
  DateTime? dateTime;
  String location;
  String notes;
  int reminderMinutes;
  String reminderLabel;

  ParsedEvent({
    this.title = '',
    this.dateTime,
    this.location = '',
    this.notes = '',
    this.reminderMinutes = 60,
    this.reminderLabel = '1 Std. vorher',
  });

  bool get isEmpty =>
      title.trim().isEmpty &&
      dateTime == null &&
      location.trim().isEmpty &&
      notes.trim().isEmpty;

  String toFormattedText() {
    String dateStr = '';
    if (dateTime != null) {
      final formatter = DateFormat("EEEE, dd.MM.yyyy 'um' HH:mm 'Uhr'", 'de_DE');
      dateStr = formatter.format(dateTime!);
    }
    return '''
Anlass: $title
Zeitpunkt: $dateStr
Ort: $location
Notizen: $notes
Erinnerungszeit: $reminderLabel'''
        .trim();
  }
}

class HomeScreen extends StatefulWidget {
  final VoidCallback onToggleTheme;
  final bool isDarkMode;

  const HomeScreen({
    super.key,
    required this.onToggleTheme,
    required this.isDarkMode,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with SingleTickerProviderStateMixin {
  late final stt.SpeechToText _speechToText;
  late final DeviceCalendarPlugin _deviceCalendarPlugin;

  bool _isSpeechInitialized = false;
  bool _isListening = false;
  int _charCount = 0;
  static const int maxCharacters = 600;

  final TextEditingController _textController = TextEditingController();
  ParsedEvent _currentEvent = ParsedEvent();
  bool _isEditMode = false;

  bool _isCustomReminderExpanded = false;
  double _customDays = 0;
  double _customHours = 1;

  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;

  @override
  void initState() {
    super.initState();
    _speechToText = stt.SpeechToText();
    _deviceCalendarPlugin = DeviceCalendarPlugin();
    _initSpeech();

    _textController.text = _currentEvent.toFormattedText();

    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    )..repeat(reverse: true);

    _pulseAnimation = Tween<double>(begin: 1.0, end: 1.25).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
  }

  Future<void> _initSpeech() async {
    try {
      _isSpeechInitialized = await _speechToText.initialize(
        onError: (val) {
          setState(() => _isListening = false);
        },
        onStatus: (val) {
          if (val == 'done' || val == 'notListening') {
            setState(() => _isListening = false);
          }
        },
      );
    } catch (_) {}
  }

  @override
  void dispose() {
    _pulseController.dispose();
    _textController.dispose();
    super.dispose();
  }

  // --- Push-to-Talk Logic (Hold-to-record & 600 Char Limit) ---
  void _startListening() async {
    if (!_isSpeechInitialized) {
      await _initSpeech();
    }
    if (_isListening) return;

    setState(() {
      _isListening = true;
      _charCount = 0;
    });

    try {
      await _speechToText.listen(
        localeId: 'de_DE',
        listenMode: stt.ListenMode.dictation,
        onResult: (SpeechRecognitionResult result) {
          String words = result.recognizedWords;
          if (words.length >= maxCharacters) {
            words = words.substring(0, maxCharacters);
            _stopListening();
          }

          setState(() {
            _charCount = words.length;
          });

          if (result.finalResult || words.length >= maxCharacters) {
            _parseSpokenGerman(words);
          }
        },
      );
    } catch (_) {
      setState(() => _isListening = false);
    }
  }

  void _stopListening() async {
    if (!_isListening) return;
    try {
      await _speechToText.stop();
    } catch (_) {}
    setState(() {
      _isListening = false;
    });
  }

  // --- On-Device Local German NLP Parser ---
  void _parseSpokenGerman(String text) {
    if (text.trim().isEmpty) return;

    String cleanText = text;
    DateTime now = DateTime.now();
    DateTime targetDate = now;
    TimeOfDay targetTime = const TimeOfDay(hour: 15, minute: 0);

    final lower = cleanText.toLowerCase();

    // 1. Date Detection
    if (lower.contains('übermorgen') || lower.contains('uebermorgen')) {
      targetDate = now.add(const Duration(days: 2));
    } else if (lower.contains('morgen') && !lower.contains('morgens')) {
      targetDate = now.add(const Duration(days: 1));
    } else {
      final weekdayMap = {
        'montag': DateTime.monday,
        'dienstag': DateTime.tuesday,
        'mittwoch': DateTime.wednesday,
        'donnerstag': DateTime.thursday,
        'freitag': DateTime.friday,
        'samstag': DateTime.saturday,
        'sonntag': DateTime.sunday,
      };
      for (final entry in weekdayMap.entries) {
        if (lower.contains(entry.key)) {
          int diff = (entry.value - now.weekday + 7) % 7;
          if (diff == 0 && lower.contains('nächsten')) diff = 7;
          targetDate = now.add(Duration(days: diff));
          break;
        }
      }
    }

    // 2. Time Detection
    final timeMatch = RegExp(r'(?:um\s+)?(\d{1,2})(?::(\d{2}))?\s*(?:uhr)?', caseSensitive: false).firstMatch(cleanText);
    if (timeMatch != null) {
      final h = int.tryParse(timeMatch.group(1) ?? '') ?? 15;
      final m = int.tryParse(timeMatch.group(2) ?? '') ?? 0;
      targetTime = TimeOfDay(hour: h.clamp(0, 23), minute: m.clamp(0, 59));
    }

    // 3. Location Detection
    String location = '';
    final locMatch = RegExp(r'\b(?:in|im|bei|am|auf)\s+(?:der\s+|dem\s+|einem\s+|einer\s+)?([\p{L}0-9\.\-\s]+?)(?=(?:\s+\b(?:um|nicht|bitte|erinnere)\b)|$)', unicode: true, caseSensitive: false).firstMatch(cleanText);
    if (locMatch != null) {
      location = locMatch.group(1)?.trim() ?? '';
      cleanText = cleanText.replaceRange(locMatch.start, locMatch.end, ' ');
    }

    // 4. Notes Detection
    String notes = '';
    final notesMatch = RegExp(r'(?:nicht vergessen|notiz:|hinweis:|mitbringen:)\s*(.+)', caseSensitive: false).firstMatch(cleanText);
    if (notesMatch != null) {
      notes = notesMatch.group(1)?.trim() ?? '';
      cleanText = cleanText.substring(0, notesMatch.start);
    }

    // 5. Clean Title
    String title = cleanText
        .replaceAll(RegExp(r'(?i)\b(?:heute|morgen|übermorgen|montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag)\b'), '')
        .replaceAll(RegExp(r'(?i)\bum\s+\d{1,2}(?::\d{2})?\s*(?:uhr)?\b'), '')
        .replaceAll(RegExp(r'(?i)\b\d{1,2}(?::\d{2})?\s*uhr\b'), '')
        .trim();

    if (title.isEmpty) title = 'Termin';
    title = title[0].toUpperCase() + title.substring(1);

    final finalDateTime = DateTime(
      targetDate.year,
      targetDate.month,
      targetDate.day,
      targetTime.hour,
      targetTime.minute,
    );

    setState(() {
      _currentEvent.title = title;
      _currentEvent.dateTime = finalDateTime;
      _currentEvent.location = location;
      _currentEvent.notes = notes;
      _textController.text = _currentEvent.toFormattedText();
      _isEditMode = false;
    });

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Spracheingabe analysiert und strukturiert')),
    );
  }

  // --- Calendar Saving ---
  Future<void> _saveToCalendar({required bool targetGoogle}) async {
    final permissionsGranted = await _deviceCalendarPlugin.hasPermissions();
    if (permissionsGranted.isSuccess && (permissionsGranted.data == false)) {
      final requestResult = await _deviceCalendarPlugin.requestPermissions();
      if (!requestResult.isSuccess || requestResult.data == false) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Kalender-Berechtigung verweigert')),
          );
        }
        return;
      }
    }

    final calendarsResult = await _deviceCalendarPlugin.retrieveCalendars();
    if (!calendarsResult.isSuccess || (calendarsResult.data?.isEmpty ?? true)) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Keine Kalender auf dem Gerät gefunden')),
        );
      }
      return;
    }

    final calendars = calendarsResult.data!;
    Calendar? selectedCal;

    if (targetGoogle) {
      selectedCal = calendars.firstWhere(
        (c) => (c.accountType?.toLowerCase().contains('google') ?? false) ||
               (c.name?.toLowerCase().contains('google') ?? false) ||
               (c.accountName?.toLowerCase().contains('gmail') ?? false),
        orElse: () => calendars.first,
      );
    } else {
      selectedCal = calendars.firstWhere((c) => c.isDefault ?? false, orElse: () => calendars.first);
    }

    final start = _currentEvent.dateTime ?? DateTime.now().add(const Duration(hours: 1));
    final end = start.add(const Duration(hours: 1));

    final eventToCreate = Event(
      selectedCal.id,
      title: _currentEvent.title.isNotEmpty ? _currentEvent.title : 'Termin',
      description: _currentEvent.notes.isNotEmpty ? '${_currentEvent.notes}\n\n(Erstellt mit VocoPlan)' : '(Erstellt mit VocoPlan)',
      location: _currentEvent.location,
      start: TZDateTime.from(start, local),
      end: TZDateTime.from(end, local),
    );

    if (_currentEvent.reminderMinutes > 0) {
      eventToCreate.reminders = [Reminder(minutes: _currentEvent.reminderMinutes)];
    }

    final createResult = await _deviceCalendarPlugin.createOrUpdateEvent(eventToCreate);
    if (mounted) {
      if (createResult?.isSuccess ?? false) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(targetGoogle
                ? 'Im Google Kalender (${selectedCal.name}) gespeichert'
                : 'Im Standard-Kalender gespeichert'),
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Fehler beim Speichern des Termins')),
        );
      }
    }
  }

  void _onReminderChipSelected(int minutes, String label) {
    setState(() {
      _currentEvent.reminderMinutes = minutes;
      _currentEvent.reminderLabel = label;
      _textController.text = _currentEvent.toFormattedText();
    });
  }

  void _onCustomReminderChanged() {
    int days = _customDays.round();
    int hours = _customHours.round();
    int totalMinutes = (days * 24 * 60) + (hours * 60);

    String label;
    if (days == 0 && hours == 0) {
      label = 'Zur Terminzeit';
    } else if (days == 0) {
      label = hours == 1 ? '1 Std. vorher' : '$hours Std. vorher';
    } else if (hours == 0) {
      label = days == 1 ? '1 Tag vorher' : '$days Tage vorher';
    } else {
      label = '$days T. und $hours Std. vorher';
    }

    setState(() {
      _currentEvent.reminderMinutes = totalMinutes;
      _currentEvent.reminderLabel = label;
      _textController.text = _currentEvent.toFormattedText();
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(
        centerTitle: true,
        title: Text(
          'VocoPlan',
          style: TextStyle(
            fontWeight: FontWeight.bold,
            color: colorScheme.primary,
            letterSpacing: 0.5,
          ),
        ),
        actions: [
          IconButton(
            icon: Icon(widget.isDarkMode ? Icons.light_mode : Icons.dark_mode),
            tooltip: 'Design wechseln',
            onPressed: widget.onToggleTheme,
          ),
        ],
        backgroundColor: Colors.transparent,
        elevation: 0,
      ),
      bottomNavigationBar: SafeArea(
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 8),
          color: colorScheme.surface.withOpacity(0.95),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              GestureDetector(
                onTapDown: (_) => _startListening(),
                onTapUp: (_) => _stopListening(),
                onTapCancel: () => _stopListening(),
                child: AnimatedBuilder(
                  animation: _pulseAnimation,
                  builder: (context, child) {
                    final scale = _isListening ? _pulseAnimation.value : 1.0;
                    return Transform.scale(
                      scale: scale,
                      child: Container(
                        width: 72,
                        height: 72,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: _isListening ? const Color(0xFFEF4444) : colorScheme.primary,
                          boxShadow: [
                            BoxShadow(
                              color: (_isListening ? const Color(0xFFEF4444) : colorScheme.primary).withOpacity(0.4),
                              blurRadius: 16,
                              spreadRadius: _isListening ? 4 : 1,
                            ),
                          ],
                        ),
                        child: Icon(
                          _isListening ? Icons.mic_off : Icons.mic,
                          color: Colors.white,
                          size: 34,
                        ),
                      ),
                    );
                  },
                ),
              ),
              const SizedBox(height: 6),
              Text(
                _isListening
                    ? (_charCount > 500
                        ? 'Zuhören... (noch ${maxCharacters - _charCount} Zeichen)'
                        : 'Ich höre zu... (beim Loslassen fertig)')
                    : 'Gedrückt halten zum Sprechen',
                style: theme.textTheme.labelSmall?.copyWith(
                  color: _isListening ? const Color(0xFFEF4444) : colorScheme.onSurfaceVariant,
                  fontWeight: _isListening ? FontWeight.bold : FontWeight.normal,
                ),
              ),
            ],
          ),
        ),
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 600),
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // 1. Großes zentrales Kartenfenster (Moderne Karten-Optik, kein Terminal-Look)
                Card(
                  shape: RoundedCornerShape(20),
                  elevation: 2,
                  color: colorScheme.surface,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Row(
                              children: [
                                Container(
                                  padding: const EdgeInsets.all(6),
                                  decoration: BoxDecoration(
                                    color: colorScheme.primary.withOpacity(0.12),
                                    shape: BoxShape.circle,
                                  ),
                                  child: Icon(Icons.event, color: colorScheme.primary, size: 18),
                                ),
                                const SizedBox(width: 8),
                                Text(
                                  'Terminkarte',
                                  style: theme.textTheme.titleMedium?.copyWith(
                                    fontWeight: FontWeight.bold,
                                    color: colorScheme.primary,
                                  ),
                                ),
                              ],
                            ),
                            if (!_currentEvent.isEmpty)
                              Row(
                                children: [
                                  IconButton(
                                    icon: Icon(_isEditMode ? Icons.check : Icons.edit, size: 18),
                                    tooltip: _isEditMode ? 'Fertig' : 'Bearbeiten',
                                    onPressed: () {
                                      setState(() {
                                        _isEditMode = !_isEditMode;
                                      });
                                    },
                                  ),
                                  IconButton(
                                    icon: const Icon(Icons.copy, size: 18),
                                    tooltip: 'Kopieren',
                                    onPressed: () {
                                      Clipboard.setData(ClipboardData(text: _currentEvent.toFormattedText()));
                                      ScaffoldMessenger.of(context).showSnackBar(
                                        const SnackBar(content: Text('In Zwischenablage kopiert')),
                                      );
                                    },
                                  ),
                                  IconButton(
                                    icon: const Icon(Icons.clear, size: 18),
                                    tooltip: 'Leeren',
                                    onPressed: () {
                                      setState(() {
                                        _currentEvent = ParsedEvent();
                                        _textController.text = '';
                                        _isEditMode = false;
                                      });
                                    },
                                  ),
                                ],
                              ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Container(
                          constraints: const BoxConstraints(minHeight: 230),
                          decoration: BoxDecoration(
                            color: colorScheme.surfaceContainerHighest.withOpacity(0.45),
                            borderRadius: BorderRadius.circular(14),
                            border: Border.all(color: colorScheme.outline.withOpacity(0.3)),
                          ),
                          padding: const EdgeInsets.all(16),
                          child: _currentEvent.isEmpty
                              ? Center(
                                  child: Column(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Container(
                                        width: 60,
                                        height: 60,
                                        decoration: BoxDecoration(
                                          shape: BoxShape.circle,
                                          color: colorScheme.primary.withOpacity(0.12),
                                        ),
                                        child: Icon(
                                          Icons.calendar_month,
                                          size: 32,
                                          color: colorScheme.primary,
                                        ),
                                      ),
                                      const SizedBox(height: 12),
                                      Text(
                                        'Bereit für deinen Termin',
                                        style: theme.textTheme.titleMedium?.copyWith(
                                          fontWeight: FontWeight.bold,
                                          color: colorScheme.onSurface,
                                        ),
                                      ),
                                      const SizedBox(height: 6),
                                      Text(
                                        'Halte die Taste gedrückt und sprich deinen Termin ganz natürlich ein.',
                                        textAlign: TextAlign.center,
                                        style: theme.textTheme.bodyMedium?.copyWith(
                                          fontSize: 13,
                                          color: colorScheme.onSurfaceVariant,
                                        ),
                                      ),
                                    ],
                                  ),
                                )
                              : (_isEditMode
                                  ? TextField(
                                      controller: _textController,
                                      maxLines: null,
                                      style: theme.textTheme.bodyMedium?.copyWith(
                                        fontSize: 15,
                                        height: 1.5,
                                      ),
                                      decoration: const InputDecoration(
                                        border: InputBorder.none,
                                        isDense: true,
                                      ),
                                    )
                                  : Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        _buildStructuredRow(
                                          icon: Icons.event,
                                          label: 'Anlass',
                                          value: _currentEvent.title.isNotEmpty ? _currentEvent.title : 'Ohne Titel',
                                          isPrimary: true,
                                          colorScheme: colorScheme,
                                          theme: theme,
                                        ),
                                        const SizedBox(height: 12),
                                        _buildStructuredRow(
                                          icon: Icons.schedule,
                                          label: 'Zeitpunkt',
                                          value: _currentEvent.dateTime != null
                                              ? DateFormat("EEEE, dd.MM.yyyy 'um' HH:mm 'Uhr'", 'de_DE').format(_currentEvent.dateTime!)
                                              : 'Kein Zeitpunkt erkannt',
                                          colorScheme: colorScheme,
                                          theme: theme,
                                        ),
                                        if (_currentEvent.location.isNotEmpty) ...[
                                          const SizedBox(height: 12),
                                          _buildStructuredRow(
                                            icon: Icons.place,
                                            label: 'Ort',
                                            value: _currentEvent.location,
                                            colorScheme: colorScheme,
                                            theme: theme,
                                          ),
                                        ],
                                        const SizedBox(height: 12),
                                        _buildStructuredRow(
                                          icon: Icons.alarm,
                                          label: 'Erinnerung',
                                          value: _currentEvent.reminderLabel,
                                          colorScheme: colorScheme,
                                          theme: theme,
                                        ),
                                        if (_currentEvent.notes.isNotEmpty) ...[
                                          const SizedBox(height: 12),
                                          _buildStructuredRow(
                                            icon: Icons.notes,
                                            label: 'Notizen',
                                            value: _currentEvent.notes,
                                            colorScheme: colorScheme,
                                            theme: theme,
                                          ),
                                        ],
                                      ],
                                    )),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // 2. Erinnerungsauswahl & aufklappbarer Regler
                Card(
                  shape: RoundedCornerShape(16),
                  elevation: 1,
                  color: colorScheme.surface,
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Row(
                              children: [
                                Icon(Icons.alarm, color: colorScheme.primary, size: 18),
                                const SizedBox(width: 8),
                                Text(
                                  'Erinnerung',
                                  style: theme.textTheme.titleSmall?.copyWith(
                                    fontWeight: FontWeight.bold,
                                    color: colorScheme.primary,
                                  ),
                                ),
                              ],
                            ),
                            TextButton.icon(
                              onPressed: () {
                                setState(() {
                                  _isCustomReminderExpanded = !_isCustomReminderExpanded;
                                });
                              },
                              icon: Icon(
                                _isCustomReminderExpanded ? Icons.expand_less : Icons.tune,
                                size: 16,
                              ),
                              label: const Text('Individuell'),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        SingleChildScrollView(
                          scrollDirection: Axis.horizontal,
                          child: Row(
                            children: [
                              _buildReminderChip('1 Std.', 60),
                              _buildReminderChip('2 Std.', 120),
                              _buildReminderChip('3 Std.', 180),
                              _buildReminderChip('1 Tag', 1440),
                              _buildReminderChip('2 Tage', 2880),
                              _buildReminderChip('3 Tage', 4320),
                              _buildReminderChip('1 Woche', 10080),
                            ],
                          ),
                        ),
                        if (_isCustomReminderExpanded) ...[
                          const Divider(height: 24),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              const Text('Tage:'),
                              Text(
                                '${_customDays.round()} Tage',
                                style: TextStyle(fontWeight: FontWeight.bold, color: colorScheme.primary),
                              ),
                            ],
                          ),
                          Slider(
                            value: _customDays,
                            min: 0,
                            max: 30,
                            divisions: 30,
                            onChanged: (val) {
                              setState(() => _customDays = val);
                              _onCustomReminderChanged();
                            },
                          ),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              const Text('Stunden:'),
                              Text(
                                '${_customHours.round()} Std.',
                                style: TextStyle(fontWeight: FontWeight.bold, color: colorScheme.primary),
                              ),
                            ],
                          ),
                          Slider(
                            value: _customHours,
                            min: 0,
                            max: 23,
                            divisions: 23,
                            onChanged: (val) {
                              setState(() => _customHours = val);
                              _onCustomReminderChanged();
                            },
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // 3. Zwei Speicher-Buttons direkt untereinander
                ElevatedButton.icon(
                  onPressed: () => _saveToCalendar(targetGoogle: false),
                  icon: const Icon(Icons.calendar_today, size: 20),
                  label: const Text(
                    'In Kalender speichern',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: colorScheme.primary,
                    foregroundColor: Colors.white,
                    minimumSize: const Size.fromHeight(52),
                    shape: RoundedCornerShape(12),
                  ),
                ),
                const SizedBox(height: 10),
                FilledButton.tonalIcon(
                  onPressed: () => _saveToCalendar(targetGoogle: true),
                  icon: const Icon(Icons.event_available, color: Color(0xFF4285F4), size: 22),
                  label: const Text(
                    'In Google Kalender speichern',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  style: FilledButton.styleFrom(
                    backgroundColor: colorScheme.surfaceContainerHighest,
                    foregroundColor: colorScheme.onSurface,
                    minimumSize: const Size.fromHeight(52),
                    shape: RoundedCornerShape(12),
                  ),
                ),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStructuredRow({
    required IconData icon,
    required String label,
    required String value,
    bool isPrimary = false,
    required ColorScheme colorScheme,
    required ThemeData theme,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(
          icon,
          size: 18,
          color: isPrimary ? colorScheme.primary : colorScheme.onSurfaceVariant,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: theme.textTheme.labelSmall?.copyWith(
                  fontSize: 11,
                  color: colorScheme.onSurfaceVariant.withOpacity(0.8),
                ),
              ),
              Text(
                value,
                style: isPrimary
                    ? theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      )
                    : theme.textTheme.bodyMedium?.copyWith(
                        fontSize: 14,
                      ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildReminderChip(String label, int minutes) {
    final isSelected = _currentEvent.reminderMinutes == minutes && !_isCustomReminderExpanded;
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: FilterChip(
        selected: isSelected,
        label: Text(label),
        onSelected: (_) => _onReminderChipSelected(minutes, '$label vorher'),
      ),
    );
  }
}
