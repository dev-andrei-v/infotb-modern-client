package ro.infotb.modern.util

enum class Locale { RO, EN }

private val translations: Map<String, Map<Locale, String>> = mapOf(
    // Navigation
    "nav.dashboard" to mapOf(Locale.RO to "Panou", Locale.EN to "Dashboard"),
    "nav.lines" to mapOf(Locale.RO to "Linii", Locale.EN to "Lines"),
    "nav.settings" to mapOf(Locale.RO to "Setari", Locale.EN to "Settings"),

    // Transport types
    "transport.BUS" to mapOf(Locale.RO to "Autobuz", Locale.EN to "Bus"),
    "transport.TRAM" to mapOf(Locale.RO to "Tramvai", Locale.EN to "Tram"),
    "transport.CABLE_CAR" to mapOf(Locale.RO to "Troleibuz", Locale.EN to "Trolleybus"),
    "transport.SUBWAY" to mapOf(Locale.RO to "Metrou", Locale.EN to "Subway"),

    // Theme
    "theme.light" to mapOf(Locale.RO to "Luminos", Locale.EN to "Light"),
    "theme.dark" to mapOf(Locale.RO to "Inchis", Locale.EN to "Dark"),
    "theme.system" to mapOf(Locale.RO to "Sistem", Locale.EN to "System"),

    // Direction
    "direction.tur" to mapOf(Locale.RO to "Tur", Locale.EN to "Tour"),
    "direction.retur" to mapOf(Locale.RO to "Retur", Locale.EN to "Retour"),

    // Dashboard page
    "dashboard.title" to mapOf(Locale.RO to "Panou", Locale.EN to "Dashboard"),
    "dashboard.stationsMonitored" to mapOf(Locale.RO to "statii monitorizate", Locale.EN to "stations monitored"),
    "dashboard.stationMonitored" to mapOf(Locale.RO to "statie monitorizata", Locale.EN to "station monitored"),
    "dashboard.addStationsDescription" to mapOf(
        Locale.RO to "Adauga statii pentru monitorizare in timp real",
        Locale.EN to "Add stations for real-time monitoring"
    ),
    "dashboard.edit" to mapOf(Locale.RO to "Editeaza", Locale.EN to "Edit"),
    "dashboard.done" to mapOf(Locale.RO to "Gata", Locale.EN to "Done"),
    "dashboard.manageGroups" to mapOf(Locale.RO to "Grupuri", Locale.EN to "Groups"),
    "dashboard.manageGroupsTitle" to mapOf(Locale.RO to "Gestioneaza grupuri", Locale.EN to "Manage groups"),
    "dashboard.manageGroupsDescription" to mapOf(
        Locale.RO to "Atinge numele ca sa-l redenumesti. Seteaza un interval orar ca grupul sa apara doar atunci, de exemplu dimineata spre serviciu si seara spre casa.",
        Locale.EN to "Tap a name to rename it. Give a group active hours to show it only then, for example mornings for the way to work and evenings for the way home."
    ),
    "dashboard.noGroups" to mapOf(Locale.RO to "Niciun grup creat.", Locale.EN to "No groups created."),
    "dashboard.reset" to mapOf(Locale.RO to "Reseteaza", Locale.EN to "Reset"),
    "dashboard.resetConfirmTitle" to mapOf(Locale.RO to "Reseteaza panoul?", Locale.EN to "Reset dashboard?"),
    "dashboard.resetConfirmDescription" to mapOf(
        Locale.RO to "Toate statiile monitorizate vor fi sterse. Aceasta actiune nu poate fi anulata.",
        Locale.EN to "All monitored stations will be removed. This action cannot be undone."
    ),
    "dashboard.cancel" to mapOf(Locale.RO to "Anuleaza", Locale.EN to "Cancel"),
    "dashboard.resetConfirm" to mapOf(Locale.RO to "Reseteaza", Locale.EN to "Reset"),
    "dashboard.emptyTitle" to mapOf(Locale.RO to "Nicio statie pe panou", Locale.EN to "No stations on dashboard"),
    "dashboard.emptyDescription" to mapOf(
        Locale.RO to "Adauga statii din pagina Linii sau foloseste butonul de mai sus pentru a monitoriza sosirile in timp real.",
        Locale.EN to "Add stations from the Lines page or use the button above to monitor arrivals in real time."
    ),
    "dashboard.updated" to mapOf(Locale.RO to "Actualizat", Locale.EN to "Updated"),
    "dashboard.updating" to mapOf(Locale.RO to "Se actualizeaza...", Locale.EN to "Updating..."),
    "dashboard.refresh" to mapOf(Locale.RO to "Reimprospateaza", Locale.EN to "Refresh"),
    "dashboard.stale" to mapOf(Locale.RO to "Date vechi", Locale.EN to "Stale"),
    "time.justNow" to mapOf(Locale.RO to "chiar acum", Locale.EN to "just now"),
    "time.secondsAgo" to mapOf(Locale.RO to "acum {n}s", Locale.EN to "{n}s ago"),
    "time.minutesAgo" to mapOf(Locale.RO to "acum {n} min", Locale.EN to "{n} min ago"),
    "widget.size" to mapOf(Locale.RO to "Marime", Locale.EN to "Size"),
    "widget.sizeMedium" to mapOf(Locale.RO to "Normal", Locale.EN to "Normal"),
    "widget.sizeLarge" to mapOf(Locale.RO to "Mare", Locale.EN to "Large"),
    "widget.sizeHint" to mapOf(
        Locale.RO to "Normal: cel mult 4 linii si 2 sosiri urmatoare. Mare: toate liniile si 3 sosiri urmatoare.",
        Locale.EN to "Normal: up to 4 lines with 2 following arrivals. Large: every line with 3 following arrivals."
    ),
    "widget.loadFailed" to mapOf(Locale.RO to "Nu s-au putut incarca sosirile", Locale.EN to "Could not load arrivals"),
    "widget.options" to mapOf(Locale.RO to "Optiuni card", Locale.EN to "Card options"),
    "widget.lines" to mapOf(Locale.RO to "Linii afisate", Locale.EN to "Shown lines"),
    "widget.linesHint" to mapOf(Locale.RO to "Atinge o linie ca s-o ascunzi sau s-o afisezi", Locale.EN to "Tap a line to hide or show it"),
    "widget.hide" to mapOf(Locale.RO to "Ascunde", Locale.EN to "Hide"),
    "widget.show" to mapOf(Locale.RO to "Afiseaza", Locale.EN to "Show"),
    "widget.hidden" to mapOf(Locale.RO to "Ascuns", Locale.EN to "Hidden"),
    "widget.visible" to mapOf(Locale.RO to "Vizibil", Locale.EN to "Visible"),
    "widget.moveUp" to mapOf(Locale.RO to "Muta in sus", Locale.EN to "Move up"),
    "widget.moveDown" to mapOf(Locale.RO to "Muta in jos", Locale.EN to "Move down"),
    "widget.newGroupName" to mapOf(Locale.RO to "Nume grup nou", Locale.EN to "New group name"),
    "dashboard.groupRename" to mapOf(Locale.RO to "Redenumeste", Locale.EN to "Rename"),
    "dashboard.groupDelete" to mapOf(Locale.RO to "Sterge grupul", Locale.EN to "Delete group"),
    "dashboard.groupSave" to mapOf(Locale.RO to "Salveaza", Locale.EN to "Save"),
    "dashboard.close" to mapOf(Locale.RO to "Inchide", Locale.EN to "Close"),
    "dashboard.groupHours" to mapOf(Locale.RO to "Interval orar", Locale.EN to "Active hours"),
    "dashboard.groupHoursDescription" to mapOf(
        Locale.RO to "Grupul apare desfasurat pe panou doar in acest interval. In restul zilei ramane restrans.",
        Locale.EN to "The group is expanded on the dashboard only between these times. The rest of the day it stays collapsed."
    ),
    "dashboard.groupHoursFrom" to mapOf(Locale.RO to "De la", Locale.EN to "From"),
    "dashboard.groupHoursTo" to mapOf(Locale.RO to "Pana la", Locale.EN to "Until"),
    "dashboard.groupHoursAlways" to mapOf(Locale.RO to "Afiseaza mereu", Locale.EN to "Always show"),
    "dashboard.groupHoursSet" to mapOf(Locale.RO to "Seteaza interval orar", Locale.EN to "Set active hours"),
    "dashboard.sortGroups" to mapOf(Locale.RO to "Pe grupuri", Locale.EN to "By group"),
    "dashboard.sortNearest" to mapOf(Locale.RO to "Cele mai apropiate", Locale.EN to "Nearest first"),
    "dashboard.locationLoading" to mapOf(Locale.RO to "Se cauta pozitia...", Locale.EN to "Finding your position..."),
    "dashboard.locationUnavailable" to mapOf(
        Locale.RO to "Pozitia nu este disponibila. Verifica permisiunea de localizare.",
        Locale.EN to "Location unavailable. Check the location permission."
    ),
    "dashboard.locationRetry" to mapOf(Locale.RO to "Reincearca", Locale.EN to "Retry"),
    "dashboard.locationUnsupported" to mapOf(
        Locale.RO to "Sortarea dupa distanta nu este disponibila pe acest dispozitiv.",
        Locale.EN to "Sorting by distance is not available on this device."
    ),
    "dashboard.allHidden" to mapOf(
        Locale.RO to "Toate statiile sunt ascunse. Apasa Editeaza pentru a le face vizibile.",
        Locale.EN to "All stations are hidden. Tap Edit to make them visible."
    ),

    // Lines page
    "lines.title" to mapOf(Locale.RO to "Linii", Locale.EN to "Lines"),
    "lines.description" to mapOf(Locale.RO to "Toate liniile de transport", Locale.EN to "All transit lines"),
    "lines.searchPlaceholder" to mapOf(
        Locale.RO to "Cauta linia (ex: 41, 61, 100, M2)...",
        Locale.EN to "Search line (e.g. 41, 61, 100, M2)..."
    ),
    "lines.errorTitle" to mapOf(Locale.RO to "Eroare la incarcarea liniilor", Locale.EN to "Error loading lines"),
    "lines.errorDescription" to mapOf(
        Locale.RO to "Verifica conexiunea la internet si incearca din nou.",
        Locale.EN to "Check your internet connection and try again."
    ),
    "lines.favorites" to mapOf(Locale.RO to "Favorite", Locale.EN to "Favorites"),
    "lines.night" to mapOf(Locale.RO to "Noapte", Locale.EN to "Night"),
    "lines.emptyTitle" to mapOf(Locale.RO to "Nicio linie gasita", Locale.EN to "No lines found"),
    "lines.emptyDescription" to mapOf(
        Locale.RO to "Incearca un alt termen de cautare sau modifica filtrele.",
        Locale.EN to "Try a different search term or change the filters."
    ),

    // Line detail page
    "lineDetail.backToLines" to mapOf(Locale.RO to "Inapoi la linii", Locale.EN to "Back to lines"),
    "lineDetail.linePrefix" to mapOf(Locale.RO to "Linia", Locale.EN to "Line"),
    "lineDetail.stations" to mapOf(Locale.RO to "statii", Locale.EN to "stations"),
    "lineDetail.showMap" to mapOf(Locale.RO to "Arata harta live", Locale.EN to "Show live map"),
    "lineDetail.goToMap" to mapOf(Locale.RO to "Vezi harta", Locale.EN to "Go to map"),
    "lineDetail.showOnMap" to mapOf(Locale.RO to "Arata pe harta", Locale.EN to "Show on map"),
    "lineDetail.notFound" to mapOf(Locale.RO to "Linia nu a fost gasita", Locale.EN to "Line not found"),
    "lineDetail.vehicleNearby" to mapOf(Locale.RO to "Vehicul in apropiere", Locale.EN to "Vehicle nearby"),
    "lineDetail.vehicleClose" to mapOf(Locale.RO to "Foarte aproape", Locale.EN to "Very close"),
    "lineDetail.vehicleApproaching" to mapOf(Locale.RO to "In apropiere", Locale.EN to "Approaching"),
    "lineDetail.vehiclesActive" to mapOf(Locale.RO to "vehicule active pe traseu", Locale.EN to "vehicles active on route"),
    "lineDetail.noVehicles" to mapOf(Locale.RO to "Niciun vehicul activ momentan", Locale.EN to "No active vehicles at the moment"),

    // Stop detail page
    "stopDetail.back" to mapOf(Locale.RO to "Inapoi", Locale.EN to "Back"),
    "stopDetail.arrivals" to mapOf(Locale.RO to "Sosiri", Locale.EN to "Arrivals"),
    "stopDetail.lines" to mapOf(Locale.RO to "linii", Locale.EN to "lines"),
    "stopDetail.noLines" to mapOf(Locale.RO to "Nicio linie la aceasta statie", Locale.EN to "No lines at this station"),
    "stopDetail.notFound" to mapOf(Locale.RO to "Statia nu a fost gasita", Locale.EN to "Station not found"),
    "stopDetail.addToDashboard" to mapOf(Locale.RO to "Panou", Locale.EN to "Dashboard"),
    "stopDetail.notification" to mapOf(Locale.RO to "Notificare", Locale.EN to "Notification"),
    "stopDetail.dynamicIsland" to mapOf(Locale.RO to "Dynamic Island", Locale.EN to "Dynamic Island"),
    "stopDetail.liveNotification" to mapOf(Locale.RO to "Live", Locale.EN to "Live"),
    "stopDetail.timetable" to mapOf(Locale.RO to "Orar", Locale.EN to "Timetable"),
    "stopDetail.timetableTitle" to mapOf(Locale.RO to "Orar linia", Locale.EN to "Timetable line"),
    "stopDetail.showOtherLines" to mapOf(Locale.RO to "Arata alte linii", Locale.EN to "Show other lines"),
    "stopDetail.hideOtherLines" to mapOf(Locale.RO to "Ascunde alte linii", Locale.EN to "Hide other lines"),
    "stopDetail.trackingStarted" to mapOf(Locale.RO to "Urmarire pornita:", Locale.EN to "Live tracking started:"),
    "stopDetail.stopTracking" to mapOf(Locale.RO to "Opreste", Locale.EN to "Stop"),
    "stopDetail.trackingStopped" to mapOf(Locale.RO to "Urmarire oprita", Locale.EN to "Live tracking stopped"),

    // Settings page
    "settings.title" to mapOf(Locale.RO to "Setari", Locale.EN to "Settings"),
    "settings.description" to mapOf(Locale.RO to "Configurare aplicatie", Locale.EN to "App configuration"),
    "settings.theme" to mapOf(Locale.RO to "Tema", Locale.EN to "Theme"),
    "settings.themeDescription" to mapOf(Locale.RO to "Alege modul de afisare", Locale.EN to "Choose display mode"),
    "settings.language" to mapOf(Locale.RO to "Limba", Locale.EN to "Language"),
    "settings.languageDescription" to mapOf(Locale.RO to "Alege limba aplicatiei", Locale.EN to "Choose app language"),
    "settings.notifications" to mapOf(Locale.RO to "Notificari active", Locale.EN to "Active notifications"),
    "settings.noNotifications" to mapOf(Locale.RO to "Nicio notificare configurata", Locale.EN to "No notifications configured"),
    "settings.notificationsActive" to mapOf(Locale.RO to "notificari active", Locale.EN to "active notifications"),
    "settings.alertAt" to mapOf(Locale.RO to "Alerta la", Locale.EN to "Alert at"),
    "settings.min" to mapOf(Locale.RO to "min", Locale.EN to "min"),
    "settings.installApp" to mapOf(Locale.RO to "Instaleaza aplicatia", Locale.EN to "Install app"),
    "settings.installDescription" to mapOf(
        Locale.RO to "Instaleaza InfoTB pe telefonul tau pentru acces rapid si notificari.",
        Locale.EN to "Install InfoTB on your phone for quick access and notifications."
    ),
    "settings.install" to mapOf(Locale.RO to "Instaleaza", Locale.EN to "Install"),
    // Backup & restore
    "settings.backup" to mapOf(Locale.RO to "Backup si restaurare", Locale.EN to "Backup & restore"),
    "settings.backupDescription" to mapOf(
        Locale.RO to "Salveaza statiile, grupurile, favoritele si notificarile intr-un fisier, ca sa le poti readuce dupa o reinstalare sau pe alt dispozitiv.",
        Locale.EN to "Save your stations, groups, favorites and notifications to a file so you can bring them back after a reinstall or on another device."
    ),
    "backup.export" to mapOf(Locale.RO to "Exporta in fisier", Locale.EN to "Export to file"),
    "backup.import" to mapOf(Locale.RO to "Importa din fisier", Locale.EN to "Import from file"),
    "backup.exported" to mapOf(Locale.RO to "Backup salvat", Locale.EN to "Backup saved"),
    "backup.cancelled" to mapOf(Locale.RO to "Anulat", Locale.EN to "Cancelled"),
    "backup.invalid" to mapOf(Locale.RO to "Fisierul nu este un backup InfoTB valid", Locale.EN to "This is not a valid InfoTB backup"),
    "backup.unsupportedVersion" to mapOf(
        Locale.RO to "Backup-ul a fost creat cu o versiune mai noua a aplicatiei",
        Locale.EN to "This backup was created by a newer version of the app"
    ),
    "backup.failed" to mapOf(Locale.RO to "Nu s-a putut finaliza", Locale.EN to "Could not complete"),
    "backup.restoreTitle" to mapOf(Locale.RO to "Restaurezi acest backup?", Locale.EN to "Restore this backup?"),
    "backup.exportedOn" to mapOf(Locale.RO to "Creat pe", Locale.EN to "Created on"),
    "backup.summaryWidgets" to mapOf(Locale.RO to "statii", Locale.EN to "stations"),
    "backup.summaryGroups" to mapOf(Locale.RO to "grupuri", Locale.EN to "groups"),
    "backup.summaryFavorites" to mapOf(Locale.RO to "linii favorite", Locale.EN to "favorite lines"),
    "backup.summarySubscriptions" to mapOf(Locale.RO to "notificari", Locale.EN to "notifications"),
    "backup.modeReplace" to mapOf(Locale.RO to "Inlocuieste", Locale.EN to "Replace"),
    "backup.modeReplaceDescription" to mapOf(
        Locale.RO to "Sterge datele curente si incarca backup-ul exact cum a fost salvat.",
        Locale.EN to "Clears current data and loads the backup exactly as saved."
    ),
    "backup.modeMerge" to mapOf(Locale.RO to "Combina", Locale.EN to "Merge"),
    "backup.modeMergeDescription" to mapOf(
        Locale.RO to "Pastreaza datele curente si adauga ce lipseste din backup.",
        Locale.EN to "Keeps current data and adds what the backup has on top."
    ),
    "backup.restore" to mapOf(Locale.RO to "Restaureaza", Locale.EN to "Restore"),
    "backup.cancel" to mapOf(Locale.RO to "Anuleaza", Locale.EN to "Cancel"),
    "backup.restored" to mapOf(Locale.RO to "Backup restaurat", Locale.EN to "Backup restored"),

    "settings.about" to mapOf(Locale.RO to "Despre", Locale.EN to "About"),
    "settings.aboutDescription" to mapOf(
        Locale.RO to "Client modern pentru transportul public din Bucuresti.",
        Locale.EN to "Modern client for Bucharest public transport."
    ),
    "settings.aboutInfo" to mapOf(
        Locale.RO to "Date furnizate de STB (Societatea de Transport Bucuresti). Informatiile in timp real sunt disponibile doar pentru vehiculele echipate cu GPS.",
        Locale.EN to "Data provided by STB (Bucharest Transport Company). Real-time information is available only for GPS-equipped vehicles."
    ),

    // Widget
    "widget.addStation" to mapOf(Locale.RO to "Adauga statie", Locale.EN to "Add station"),
    "widget.chooseLine" to mapOf(Locale.RO to "Alege linia", Locale.EN to "Choose line"),
    "widget.direction" to mapOf(Locale.RO to "Directie", Locale.EN to "Direction"),
    "widget.chooseStation" to mapOf(Locale.RO to "Alege statia", Locale.EN to "Choose station"),
    "widget.searchLine" to mapOf(Locale.RO to "Cauta linia...", Locale.EN to "Search line..."),
    "widget.continue" to mapOf(Locale.RO to "Continua", Locale.EN to "Continue"),
    "widget.filterLines" to mapOf(Locale.RO to "Filtreaza linii", Locale.EN to "Filter lines"),
    "widget.group" to mapOf(Locale.RO to "Grup", Locale.EN to "Group"),
    "widget.noGroup" to mapOf(Locale.RO to "Fara grup", Locale.EN to "No group"),
    "widget.addGroup" to mapOf(Locale.RO to "Adauga grup...", Locale.EN to "Add group..."),
    "widget.delete" to mapOf(Locale.RO to "Sterge", Locale.EN to "Delete"),
    "widget.moreLines" to mapOf(Locale.RO to "linii in plus", Locale.EN to "more lines"),
    "widget.moreNews" to mapOf(Locale.RO to "+{n} alte anunturi", Locale.EN to "+{n} more notices"),
    "widget.noData" to mapOf(Locale.RO to "Fara date", Locale.EN to "No data"),
    "widget.outOfService" to mapOf(Locale.RO to "Nu circula", Locale.EN to "Not in service"),
    "widget.nextArrivals" to mapOf(Locale.RO to "Urmatoarele", Locale.EN to "Next"),
    "widget.noScheduled" to mapOf(Locale.RO to "Nicio cursă programată în următoarele ore", Locale.EN to "No scheduled arrivals in the next hours"),
    "widget.addToDashboard" to mapOf(Locale.RO to "Adauga pe panou", Locale.EN to "Add to dashboard"),
    "widget.addedToDashboard" to mapOf(Locale.RO to "adaugat pe panou", Locale.EN to "added to dashboard"),
    "widget.widgetAdded" to mapOf(Locale.RO to "Widget adaugat pe panou", Locale.EN to "Widget added to dashboard"),

    // Arrival time
    "arrival.scheduled" to mapOf(Locale.RO to "Orar programat", Locale.EN to "Scheduled timetable"),
    "arrival.realtime" to mapOf(Locale.RO to "Timp real GPS", Locale.EN to "Real-time GPS"),
    "arrival.atStation" to mapOf(Locale.RO to "In statie", Locale.EN to "At station"),
    "arrival.atStationScheduled" to mapOf(
        Locale.RO to "Ar trebui sa fie in statie (conform orarului)",
        Locale.EN to "Should be at station (according to schedule)"
    ),
    "timetable.hour" to mapOf(Locale.RO to "Ora", Locale.EN to "Hour"),
    "timetable.minutes" to mapOf(Locale.RO to "Minute", Locale.EN to "Minutes"),
    "timetable.highlightRow" to mapOf(Locale.RO to "Ora curenta", Locale.EN to "Current hour"),
    "timetable.highlightMinute" to mapOf(Locale.RO to "Urmatoarea plecare", Locale.EN to "Next departure"),

    // Announcements
    "nav.announcements" to mapOf(Locale.RO to "Anunturi", Locale.EN to "News"),
    "announcements.title" to mapOf(Locale.RO to "Anunturi STB", Locale.EN to "STB Announcements"),
    "announcements.description" to mapOf(Locale.RO to "Anunturi oficiale de la STB", Locale.EN to "Official announcements from STB"),
    "announcements.errorTitle" to mapOf(Locale.RO to "Eroare la incarcarea anunturilor", Locale.EN to "Error loading announcements"),
    "announcements.errorDescription" to mapOf(
        Locale.RO to "Verifica conexiunea la internet si incearca din nou.",
        Locale.EN to "Check your internet connection and try again."
    ),
    "announcements.emptyTitle" to mapOf(Locale.RO to "Niciun anunt", Locale.EN to "No announcements"),
    "announcements.today" to mapOf(Locale.RO to "Azi", Locale.EN to "Today"),
    "announcements.yesterday" to mapOf(Locale.RO to "Ieri", Locale.EN to "Yesterday"),
    "announcements.showMore" to mapOf(Locale.RO to "Citeste mai mult", Locale.EN to "Read more"),
    "announcements.showLess" to mapOf(Locale.RO to "Arata mai putin", Locale.EN to "Show less"),
    "announcements.relatedAlert" to mapOf(Locale.RO to "Anunt STB", Locale.EN to "STB announcement"),

    // Notifications page
    "nav.notifications" to mapOf(Locale.RO to "Notificari", Locale.EN to "Notifications"),
    "notifications.title" to mapOf(Locale.RO to "Notificari", Locale.EN to "Notifications"),
    "liveTracking.title" to mapOf(Locale.RO to "Urmarire live", Locale.EN to "Live tracking"),
    "liveTracking.enabled" to mapOf(Locale.RO to "Urmarire live activa", Locale.EN to "Live tracking active"),
    "liveTracking.emptyTitle" to mapOf(Locale.RO to "Nicio urmarire activa", Locale.EN to "No active tracking"),
    "liveTracking.emptyDescription" to mapOf(
        Locale.RO to "Apasa pe butonul Live de pe o statie pentru a urmari sosirea in timp real.",
        Locale.EN to "Tap the Live button on a stop to track arrivals in real-time."
    ),
    "notifications.description" to mapOf(Locale.RO to "Gestioneaza notificarile pentru sosiri", Locale.EN to "Manage arrival notifications"),
    "notifications.active" to mapOf(Locale.RO to "notificari active", Locale.EN to "active notifications"),
    "notifications.emptyTitle" to mapOf(Locale.RO to "Nicio notificare", Locale.EN to "No notifications"),
    "notifications.emptyDescription" to mapOf(
        Locale.RO to "Adauga notificari din paginile statiilor pentru a fi alertat cand autobuzul soseste.",
        Locale.EN to "Add notifications from stop pages to be alerted when the bus arrives."
    ),

    // Dynamic Island (iOS)
    "dynamicIsland.title" to mapOf(Locale.RO to "Dynamic Island", Locale.EN to "Dynamic Island"),
    "dynamicIsland.enabled" to mapOf(Locale.RO to "Dynamic Island activat", Locale.EN to "Dynamic Island enabled"),
    "dynamicIsland.emptyTitle" to mapOf(Locale.RO to "Dynamic Island inactiv", Locale.EN to "Dynamic Island inactive"),
    "dynamicIsland.emptyDescription" to mapOf(
        Locale.RO to "Adauga notificari din paginile statiilor pentru a activa Dynamic Island.",
        Locale.EN to "Add notifications from stop pages to activate the Dynamic Island."
    ),
    "notifications.onceSummary" to mapOf(Locale.RO to "Notificare la {min} min inainte", Locale.EN to "Notify {min} min before"),
    "notifications.repeatSummary" to mapOf(Locale.RO to "Repeta la fiecare {min} min", Locale.EN to "Repeat every {min} min"),
    "notifications.every" to mapOf(Locale.RO to "la fiecare", Locale.EN to "every"),
    "notifications.disable" to mapOf(Locale.RO to "Dezactiveaza", Locale.EN to "Disable"),
    "notifications.enable" to mapOf(Locale.RO to "Activeaza", Locale.EN to "Enable"),

    "notification.title" to mapOf(Locale.RO to "Linia", Locale.EN to "Line"),
    "notification.arrivesAt" to mapOf(Locale.RO to "Soseste la", Locale.EN to "Arrives at"),
    "notification.in" to mapOf(Locale.RO to "in", Locale.EN to "in"),
    "notification.minute" to mapOf(Locale.RO to "minut", Locale.EN to "minute"),
    "notification.minutes" to mapOf(Locale.RO to "minute", Locale.EN to "minutes"),
    "notification.arrived" to mapOf(Locale.RO to "A sosit!", Locale.EN to "Arrived!"),
    "notification.now" to mapOf(Locale.RO to "ACUM", Locale.EN to "NOW"),
    "notification.min" to mapOf(Locale.RO to "min", Locale.EN to "min"),
    "notification.stop" to mapOf(Locale.RO to "statie", Locale.EN to "stop"),
    "notification.stops" to mapOf(Locale.RO to "statii", Locale.EN to "stops"),
    "notification.scheduled" to mapOf(Locale.RO to "Programat", Locale.EN to "Scheduled"),
    "notification.realtime" to mapOf(Locale.RO to "Timp real", Locale.EN to "Real-time"),
    "notification.setFor" to mapOf(Locale.RO to "Notificare setata pentru linia", Locale.EN to "Notification set for line"),
    "notification.setup" to mapOf(Locale.RO to "Seteaza notificare", Locale.EN to "Set up notification"),
    "notification.mode" to mapOf(Locale.RO to "Tip notificare", Locale.EN to "Notification type"),
    "notification.modeOnce" to mapOf(Locale.RO to "O singura data", Locale.EN to "Once"),
    "notification.modeRepeat" to mapOf(Locale.RO to "Repetitiv", Locale.EN to "Repeating"),
    "notification.notifyWhen" to mapOf(Locale.RO to "Notifica-ma cand mai sunt", Locale.EN to "Notify me when"),
    "notification.repeatEvery" to mapOf(Locale.RO to "Repeta la fiecare", Locale.EN to "Repeat every"),
    "notification.onceDescription" to mapOf(
        Locale.RO to "Vei primi o notificare cand autobuzul este la {min} minute distanta.",
        Locale.EN to "You will be notified when the bus is {min} minutes away."
    ),
    "notification.repeatDescription" to mapOf(
        Locale.RO to "Vei primi notificari la fiecare {min} minute pana cand autobuzul soseste.",
        Locale.EN to "You will receive notifications every {min} minutes until the bus arrives."
    ),
    "notification.activate" to mapOf(Locale.RO to "Activeaza notificarea", Locale.EN to "Activate notification"),

    // Misc
    "misc.vehicle" to mapOf(Locale.RO to "Vehicul", Locale.EN to "Vehicle")
)

fun t(key: String, locale: Locale): String {
    return translations[key]?.get(locale) ?: key
}
