(function () {
  "use strict";

  var app = angular.module("adminApp", ["ngRoute"]);

  function emptyPermissions() {
    return {
      events: { read: false, create: false, update: false, delete: false, approve: false },
      stations: { read: false, create: false, update: false, delete: false, approve: false },
      categories: { read: false, create: false, update: false, delete: false, approve: false },
      audioLinks: { read: false, create: false, update: false, delete: false, approve: false },
      users: { read: false, create: false, update: false, delete: false, approve: false }
    };
  }

  app.config(["$routeProvider", "$locationProvider", "$httpProvider",
    function ($routeProvider, $locationProvider, $httpProvider) {
      $locationProvider.hashPrefix("");
      $routeProvider
        .when("/dashboard", { templateUrl: "dashboard.html", controller: "DashboardCtrl" })
        .when("/events", { templateUrl: "events.html", controller: "EventsCtrl" })
        .when("/stations", { templateUrl: "stations.html", controller: "StationsCtrl" })
        .when("/categories", { templateUrl: "categories.html", controller: "CategoriesCtrl" })
        .when("/links", { templateUrl: "links.html", controller: "LinksCtrl" })
        .when("/users", { templateUrl: "users.html", controller: "UsersCtrl" })
        .otherwise({ redirectTo: "/dashboard" });
      $httpProvider.interceptors.push("AuthInterceptor");
    }]);

  app.factory("AuthService", ["$window", function ($window) {
    var tokenKey = "radioAdminToken";
    var profileKey = "radioAdminProfile";
    return {
      token: function () { return $window.localStorage.getItem(tokenKey); },
      profile: function () {
        try { return JSON.parse($window.localStorage.getItem(profileKey) || "null"); }
        catch (e) { return null; }
      },
      save: function (token, profile) {
        $window.localStorage.setItem(tokenKey, token);
        $window.localStorage.setItem(profileKey, JSON.stringify(profile || {}));
      },
      clear: function () {
        $window.localStorage.removeItem(tokenKey);
        $window.localStorage.removeItem(profileKey);
      }
    };
  }]);

  app.factory("AuthInterceptor", ["AuthService", "$q", "$rootScope",
    function (AuthService, $q, $rootScope) {
      return {
        request: function (config) {
          var token = AuthService.token();
          if (token) {
            config.headers = config.headers || {};
            config.headers.Authorization = "Bearer " + token;
          }
          return config;
        },
        responseError: function (rejection) {
          if (rejection.status === 401) {
            AuthService.clear();
            $rootScope.$broadcast("admin:unauthorized");
          } else if (rejection.status === 403) {
            $rootScope.flash = {
              error: true,
              text: (rejection.data && rejection.data.message) || "You do not have permission for this action."
            };
          }
          return $q.reject(rejection);
        }
      };
    }]);

  app.factory("Api", ["$http", function ($http) {
    var base = "/api/admin";
    function list(path, q) {
      return $http.get(base + path, { params: q ? { q: q } : {} });
    }
    return {
      login: function (body) { return $http.post(base + "/login", body); },
      me: function () { return $http.get(base + "/me"); },
      stats: function () { return $http.get(base + "/stats"); },
      reload: function () { return $http.post(base + "/cache/reload"); },
      events: function (q) { return list("/events", q); },
      saveEvent: function (item) {
        return item._id ? $http.put(base + "/events/" + item._id, item) : $http.post(base + "/events", item);
      },
      deleteEvent: function (id) { return $http.delete(base + "/events/" + id); },
      approve: function (id, note) { return $http.post(base + "/events/" + id + "/approve", { reviewNote: note || "" }); },
      reject: function (id, note) { return $http.post(base + "/events/" + id + "/reject", { reviewNote: note || "" }); },
      stations: function (q) { return list("/stations", q); },
      saveStation: function (item) {
        return item._id ? $http.put(base + "/stations/" + item._id, item) : $http.post(base + "/stations", item);
      },
      deleteStation: function (id) { return $http.delete(base + "/stations/" + id); },
      categories: function (q) { return list("/categories", q); },
      saveCategory: function (item) {
        return item._id ? $http.put(base + "/categories/" + item._id, item) : $http.post(base + "/categories", item);
      },
      deleteCategory: function (id) { return $http.delete(base + "/categories/" + id); },
      links: function (q) { return list("/audio-links", q); },
      saveLink: function (item) {
        return item._id ? $http.put(base + "/audio-links/" + item._id, item) : $http.post(base + "/audio-links", item);
      },
      deleteLink: function (id) { return $http.delete(base + "/audio-links/" + id); },
      users: function (q) { return list("/users", q); },
      saveUser: function (item) {
        return item.id ? $http.put(base + "/users/" + item.id, item) : $http.post(base + "/users", item);
      },
      deleteUser: function (id) { return $http.delete(base + "/users/" + id); }
    };
  }]);

  app.filter("stationName", function () {
    return function (station) {
      if (!station) { return ""; }
      var tr = station.translations || {};
      return (tr.en && tr.en.name) || (tr.hi && tr.hi.name) || station.category || station._id;
    };
  });

  app.filter("categoryName", function () {
    return function (category) {
      if (!category) { return ""; }
      var tr = category.translations || {};
      return (tr.en && tr.en.name) || category.category || category._id;
    };
  });

  // Keep datetime-local as YYYY-MM-DDTHH:mm strings so the browser does not reject Date/ISO values.
  app.directive("localDatetime", function () {
    return {
      restrict: "A",
      require: "ngModel",
      priority: 10,
      link: function (scope, element, attrs, ngModel) {
        ngModel.$formatters = [];
        ngModel.$parsers = [];
        ngModel.$validators = {};
        ngModel.$formatters.push(function (value) { return value || ""; });
        ngModel.$parsers.push(function (value) { return value || ""; });
      }
    };
  });

  app.controller("ShellCtrl", ["$scope", "$rootScope", "$location", "AuthService", "Api",
    function ($scope, $rootScope, $location, AuthService, Api) {
      $scope.auth = { username: "", password: "", error: "", loading: false };
      $scope.sidebarOpen = false;
      $scope.profile = AuthService.profile();
      $scope.isAuthed = !!AuthService.token();
      $rootScope.flash = null;

      $scope.can = function (module, action) {
        if (!$scope.profile) { return false; }
        if ($scope.profile.superAdmin) { return true; }
        var perm = $scope.profile.permissions && $scope.profile.permissions[module];
        if (!perm) { return false; }
        if (action === "read") {
          return !!(perm.read || perm.create || perm.update || perm.delete || perm.approve);
        }
        return !!perm[action];
      };

      $scope.isActive = function (path) { return $location.path() === path; };
      $scope.toggleSidebar = function () { $scope.sidebarOpen = !$scope.sidebarOpen; };
      $scope.closeSidebar = function () { $scope.sidebarOpen = false; };
      $scope.clearFlash = function () { $rootScope.flash = null; };

      function afterLogin(profile) {
        $scope.profile = profile;
        $scope.isAuthed = true;
        $location.path("/dashboard");
      }

      $scope.login = function () {
        $scope.auth.loading = true;
        $scope.auth.error = "";
        Api.login({ username: $scope.auth.username, password: $scope.auth.password })
          .then(function (res) {
            AuthService.save(res.data.token, res.data.profile);
            afterLogin(res.data.profile);
          })
          .catch(function (err) {
            $scope.auth.error = (err.data && err.data.message) || "Invalid username or password";
          })
          .finally(function () { $scope.auth.loading = false; });
      };

      $scope.logout = function () {
        AuthService.clear();
        $scope.isAuthed = false;
        $scope.profile = null;
      };

      $scope.$on("admin:unauthorized", function () {
        $scope.isAuthed = false;
        $scope.profile = null;
      });

      if ($scope.isAuthed) {
        Api.me().then(function (res) {
          AuthService.save(AuthService.token(), res.data);
          $scope.profile = res.data;
        }).catch(function () { $scope.logout(); });
      }
    }]);

  function bindList($scope, Api, loader) {
    $scope.query = "";
    $scope.refreshing = false;
    $scope.formError = "";
    $scope.search = function () { loader(); };
    $scope.refresh = function () {
      $scope.refreshing = true;
      Api.reload().then(function (res) {
        $scope.cacheMeta = res.data;
        loader();
      }).finally(function () { $scope.refreshing = false; });
    };
    $scope.closeForm = function () {
      $scope.form = null;
      $scope.formError = "";
    };
    function onEscape(event) {
      if (event.keyCode === 27 && $scope.form) {
        $scope.$apply($scope.closeForm);
      }
    }
    angular.element(document).on("keydown", onEscape);
    $scope.$on("$destroy", function () {
      angular.element(document).off("keydown", onEscape);
    });
  }

  function flashError($scope, err, fallback) {
    var message = (err && err.data && (err.data.message || err.data.error)) || fallback;
    $scope.formError = message;
    $scope.$root.flash = { error: true, text: message };
  }

  function toDatetimeLocal(value) {
    if (!value) { return ""; }
    var date = value instanceof Date ? value : new Date(value);
    if (isNaN(date.getTime())) { return ""; }
    var pad = function (n) { return (n < 10 ? "0" : "") + n; };
    return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate())
      + "T" + pad(date.getHours()) + ":" + pad(date.getMinutes());
  }

  function fromDatetimeLocal(value) {
    if (!value) { return null; }
    var date = value instanceof Date ? value : new Date(value);
    return isNaN(date.getTime()) ? null : date.toISOString();
  }

  app.controller("DashboardCtrl", ["$scope", "Api", function ($scope, Api) {
    $scope.stats = {};
    function load() {
      Api.stats().then(function (res) { $scope.stats = res.data; });
    }
    $scope.refresh = function () {
      Api.reload().then(function () {
        $scope.$root.flash = { text: "Catalog reloaded from MongoDB into cache." };
        load();
      });
    };
    load();
  }]);

  app.controller("EventsCtrl", ["$scope", "Api", function ($scope, Api) {
    $scope.filter = "pending";
    $scope.events = [];
    $scope.form = null;
    bindList($scope, Api, load);
    function load() {
      Api.events($scope.query).then(function (res) { $scope.events = res.data || []; });
    }
    $scope.filtered = function () {
      return ($scope.events || []).filter(function (event) {
        var status = (event.approvalStatus || "approved").toLowerCase();
        return $scope.filter === "all" || status === $scope.filter;
      });
    };
    $scope.openCreate = function () {
      $scope.formError = "";
      var start = toDatetimeLocal(new Date());
      $scope.form = {
        title: "", city: "", address: "", organizedBy: "", organization: "", category: "",
        description: "", status: "scheduled", approvalStatus: "approved",
        date: start, end_date: start
      };
    };
    $scope.openEdit = function (event) {
      $scope.formError = "";
      $scope.form = angular.copy(event);
      $scope.form.date = toDatetimeLocal(event.date);
      $scope.form.end_date = toDatetimeLocal(event.end_date || event.endDate || event.date);
    };
    $scope.save = function () {
      if (!$scope.form.title || !$scope.form.city || !$scope.form.date) {
        $scope.formError = "Please fill Title, City, and Start date.";
        return;
      }
      var payload = angular.copy($scope.form);
      delete payload.$$hashKey;
      payload.date = fromDatetimeLocal(payload.date);
      payload.end_date = fromDatetimeLocal(payload.end_date) || payload.date;
      Api.saveEvent(payload).then(function () {
        $scope.closeForm();
        load();
        $scope.$root.flash = { text: "Event saved." };
      }).catch(function (err) { flashError($scope, err, "Could not save event."); });
    };
    $scope.remove = function (event) {
      if (window.confirm("Delete this event?")) {
        Api.deleteEvent(event._id).then(load).catch(function (err) {
          flashError($scope, err, "Could not delete event.");
        });
      }
    };
    $scope.approve = function (event) { Api.approve(event._id, event.reviewNote).then(load); };
    $scope.reject = function (event) {
      var note = window.prompt("Rejection note (optional)", event.reviewNote || "");
      if (note !== null) { Api.reject(event._id, note).then(load); }
    };
    load();
  }]);

  app.controller("StationsCtrl", ["$scope", "Api", function ($scope, Api) {
    $scope.stations = [];
    $scope.categories = [];
    $scope.form = null;
    bindList($scope, Api, load);
    function load() {
      Api.stations($scope.query).then(function (res) { $scope.stations = res.data || []; });
      Api.categories().then(function (res) { $scope.categories = res.data || []; });
    }
    $scope.openCreate = function () {
      $scope.formError = "";
      $scope.form = { live: true, play_mode: "sequence", type: "radio", language: "en", nameEn: "", nameHi: "", category: "" };
    };
    $scope.openEdit = function (station) {
      $scope.formError = "";
      $scope.form = angular.copy(station);
      $scope.form.nameEn = (station.translations && station.translations.en && station.translations.en.name) || "";
      $scope.form.nameHi = (station.translations && station.translations.hi && station.translations.hi.name) || "";
      $scope.form.play_mode = station.play_mode || station.playMode || "sequence";
    };
    $scope.save = function () {
      if (!$scope.form.nameEn) {
        $scope.formError = "Please enter the English name.";
        return;
      }
      var payload = angular.copy($scope.form);
      payload.translations = { en: { name: payload.nameEn || "" }, hi: { name: payload.nameHi || payload.nameEn || "" } };
      delete payload.nameEn;
      delete payload.nameHi;
      Api.saveStation(payload).then(function () {
        $scope.closeForm();
        load();
        $scope.$root.flash = { text: "Station saved." };
      }).catch(function (err) { flashError($scope, err, "Could not save station."); });
    };
    $scope.remove = function (station) {
      if (window.confirm("Delete this station and its audio links?")) {
        Api.deleteStation(station._id).then(load).catch(function (err) {
          flashError($scope, err, "Could not delete station.");
        });
      }
    };
    load();
  }]);

  app.controller("CategoriesCtrl", ["$scope", "Api", function ($scope, Api) {
    $scope.categories = [];
    $scope.form = null;
    bindList($scope, Api, load);
    function load() {
      Api.categories($scope.query).then(function (res) { $scope.categories = res.data || []; });
    }
    $scope.openCreate = function () {
      $scope.formError = "";
      $scope.form = { order: 1, icon: "music_note", nameEn: "", nameHi: "", category: "" };
    };
    $scope.openEdit = function (category) {
      $scope.formError = "";
      $scope.form = angular.copy(category);
      $scope.form.nameEn = (category.translations && category.translations.en && category.translations.en.name) || "";
      $scope.form.nameHi = (category.translations && category.translations.hi && category.translations.hi.name) || "";
    };
    $scope.save = function () {
      if (!$scope.form.category || !$scope.form.nameEn) {
        $scope.formError = "Please fill Key and English name.";
        return;
      }
      var payload = angular.copy($scope.form);
      payload.translations = {
        en: { name: payload.nameEn || payload.category },
        hi: { name: payload.nameHi || payload.nameEn || payload.category }
      };
      delete payload.nameEn;
      delete payload.nameHi;
      Api.saveCategory(payload).then(function () {
        $scope.closeForm();
        load();
        $scope.$root.flash = { text: "Category saved." };
      }).catch(function (err) { flashError($scope, err, "Could not save category."); });
    };
    $scope.remove = function (category) {
      if (window.confirm("Delete this category?")) {
        Api.deleteCategory(category._id).then(load).catch(function (err) {
          flashError($scope, err, "Could not delete category.");
        });
      }
    };
    load();
  }]);

  app.controller("LinksCtrl", ["$scope", "Api", function ($scope, Api) {
    $scope.links = [];
    $scope.stations = [];
    $scope.stationFilter = "";
    $scope.form = null;
    bindList($scope, Api, load);
    function load() {
      Api.links($scope.query).then(function (res) { $scope.links = res.data || []; });
      Api.stations().then(function (res) { $scope.stations = res.data || []; });
    }
    $scope.filtered = function () {
      if (!$scope.stationFilter) { return $scope.links; }
      return ($scope.links || []).filter(function (link) { return link.station_id === $scope.stationFilter; });
    };
    $scope.openCreate = function () {
      $scope.formError = "";
      $scope.form = { played: false, status: "N", sequence: 1, nameEn: "", station_id: $scope.stationFilter || "" };
    };
    $scope.openEdit = function (link) {
      $scope.formError = "";
      $scope.form = angular.copy(link);
      $scope.form.nameEn = (link.translations && link.translations.en && link.translations.en.name) || "";
      $scope.form.station_id = link.station_id || link.stationId;
    };
    $scope.save = function () {
      if (!$scope.form.nameEn || !$scope.form.url || !$scope.form.station_id) {
        $scope.formError = "Please fill Title, Station, and URL.";
        return;
      }
      var payload = angular.copy($scope.form);
      payload.translations = { en: { name: payload.nameEn || "Track" } };
      delete payload.nameEn;
      Api.saveLink(payload).then(function () {
        $scope.closeForm();
        load();
        $scope.$root.flash = { text: "Audio link saved." };
      }).catch(function (err) { flashError($scope, err, "Could not save audio link."); });
    };
    $scope.remove = function (link) {
      if (window.confirm("Delete this audio link?")) {
        Api.deleteLink(link._id).then(load).catch(function (err) {
          flashError($scope, err, "Could not delete audio link.");
        });
      }
    };
    load();
  }]);

  app.controller("UsersCtrl", ["$scope", "Api", function ($scope, Api) {
    $scope.users = [];
    $scope.categories = [];
    $scope.form = null;
    $scope.modules = [
      { key: "events", label: "Events", approve: true },
      { key: "stations", label: "Stations" },
      { key: "categories", label: "Categories" },
      { key: "audioLinks", label: "Audio links" },
      { key: "users", label: "Sub-admins" }
    ];
    bindList($scope, Api, load);
    function load() {
      Api.users($scope.query).then(function (res) { $scope.users = res.data || []; });
      Api.categories().then(function (res) { $scope.categories = res.data || []; }).catch(function () {});
    }
    $scope.openCreate = function () {
      $scope.formError = "";
      $scope.form = {
        username: "", password: "", displayName: "", organization: "",
        role: "SUB_ADMIN", enabled: true, ownRecordsOnly: false,
        permissions: emptyPermissions(), allowedCategoryKeys: [], allowedOrganizationsText: ""
      };
    };
    $scope.openEdit = function (user) {
      $scope.formError = "";
      $scope.form = angular.copy(user);
      $scope.form.password = "";
      $scope.form.permissions = angular.merge(emptyPermissions(), user.permissions || {});
      $scope.form.allowedOrganizationsText = (user.allowedOrganizations || []).join(", ");
      $scope.form.role = user.superAdmin ? "SUPER_ADMIN" : "SUB_ADMIN";
    };
    $scope.toggleCategory = function (key) {
      $scope.form.allowedCategoryKeys = $scope.form.allowedCategoryKeys || [];
      var idx = $scope.form.allowedCategoryKeys.indexOf(key);
      if (idx >= 0) { $scope.form.allowedCategoryKeys.splice(idx, 1); }
      else { $scope.form.allowedCategoryKeys.push(key); }
    };
    $scope.hasCategory = function (key) {
      return $scope.form && ($scope.form.allowedCategoryKeys || []).indexOf(key) >= 0;
    };
    $scope.save = function () {
      if (!$scope.form.username || (!$scope.form.id && (!$scope.form.password || $scope.form.password.length < 8))) {
        $scope.formError = "Username is required. New users need a password of at least 8 characters.";
        return;
      }
      var payload = angular.copy($scope.form);
      payload.allowedOrganizations = (payload.allowedOrganizationsText || "")
        .split(",").map(function (s) { return s.trim(); }).filter(Boolean);
      delete payload.allowedOrganizationsText;
      delete payload.superAdmin;
      if (!payload.password) { delete payload.password; }
      Api.saveUser(payload).then(function () {
        $scope.closeForm();
        load();
        $scope.$root.flash = { text: "User saved." };
      }).catch(function (err) { flashError($scope, err, "Could not save user."); });
    };
    $scope.remove = function (user) {
      if (window.confirm("Delete sub-admin " + user.username + "?")) {
        Api.deleteUser(user.id).then(load).catch(function (err) {
          flashError($scope, err, "Could not delete user.");
        });
      }
    };
    load();
  }]);
})();
