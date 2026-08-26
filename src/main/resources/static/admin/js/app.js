(function () {
  "use strict";

  var app = angular.module("adminApp", ["ngRoute"]);
  var PAGE_SIZE = 20;
  var LANGUAGES = [
    { code: "en", name: "English" },
    { code: "pa", name: "Punjabi" },
    { code: "hi", name: "Hindi" }
  ];
  var STATION_TYPES = [
    { code: "radio", name: "Radio" },
    { code: "audio", name: "Audio" },
    { code: "live", name: "Live Stream" }
  ];
  var CATEGORY_ICONS = [
    { value: "music_note", label: "Music Note" },
    { value: "book", label: "Book" },
    { value: "mic", label: "Microphone" },
    { value: "headset", label: "Headset" },
    { value: "library_music", label: "Library Music" },
    { value: "album", label: "Album" },
    { value: "radio", label: "Radio" },
    { value: "audiotrack", label: "Audio Track" },
    { value: "spa", label: "Spa" },
    { value: "self_improvement", label: "Self Improvement" },
    { value: "menu_book", label: "Menu Book" },
    { value: "podcasts", label: "Podcasts" },
    { value: "favorite", label: "Favorite" },
    { value: "temple_hindu", label: "Temple" }
  ];
  var LANGUAGE_NAMES = { en: "English", pa: "Punjabi", hi: "Hindi" };
  var TYPE_NAMES = { radio: "Radio", audio: "Audio", live: "Live Stream" };

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
        .when("/settings", { templateUrl: "settings.html", controller: "SettingsCtrl" })
        .otherwise({ redirectTo: "/dashboard" });
      $httpProvider.interceptors.push("AuthInterceptor");
    }]);

  app.run(["$rootScope", function ($rootScope) {
    $rootScope.languages = LANGUAGES;
    $rootScope.stationTypes = STATION_TYPES;
    $rootScope.categoryIcons = CATEGORY_ICONS;
    $rootScope.busy = null;
    $rootScope.showBusy = function () {};
    $rootScope.hideBusy = function () {};
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

  app.factory("CatalogStore", ["$window", function ($window) {
    var mem = {};
    function storageKey(name) { return "radioAdminCache:" + name; }
    return {
      get: function (name) {
        if (Object.prototype.hasOwnProperty.call(mem, name)) { return mem[name]; }
        try { mem[name] = JSON.parse($window.sessionStorage.getItem(storageKey(name)) || "null"); }
        catch (e) { mem[name] = null; }
        return mem[name];
      },
      set: function (name, value) {
        mem[name] = value;
        try { $window.sessionStorage.setItem(storageKey(name), JSON.stringify(value)); } catch (e) {}
      },
      clear: function () {
        mem = {};
        try {
          Object.keys($window.sessionStorage).forEach(function (key) {
            if (key.indexOf("radioAdminCache:") === 0) {
              $window.sessionStorage.removeItem(key);
            }
          });
        } catch (e) {}
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
    function page(path, params) {
      var query = { page: params && params.page || 0, size: params && params.size || PAGE_SIZE };
      if (params && params.q) { query.q = params.q; }
      if (params && params.status) { query.status = params.status; }
      if (params && params.stationId) { query.stationId = params.stationId; }
      return $http.get(base + path, { params: query });
    }
    return {
      login: function (body) { return $http.post(base + "/login", body); },
      me: function () { return $http.get(base + "/me"); },
      stats: function () { return $http.get(base + "/stats"); },
      reload: function () { return $http.post(base + "/cache/reload"); },
      event: function (id) { return $http.get(base + "/events/" + id); },
      events: function (params) { return page("/events", params); },
      saveEvent: function (item) {
        return item._id ? $http.put(base + "/events/" + item._id, item) : $http.post(base + "/events", item);
      },
      deleteEvent: function (id) { return $http.delete(base + "/events/" + id); },
      approve: function (id, note) { return $http.post(base + "/events/" + id + "/approve", { reviewNote: note || "" }); },
      reject: function (id, note) { return $http.post(base + "/events/" + id + "/reject", { reviewNote: note || "" }); },
      stations: function (params) { return page("/stations", params); },
      saveStation: function (item) {
        return item._id ? $http.put(base + "/stations/" + item._id, item) : $http.post(base + "/stations", item);
      },
      deleteStation: function (id) { return $http.delete(base + "/stations/" + id); },
      categories: function (params) { return page("/categories", params); },
      saveCategory: function (item) {
        return item._id ? $http.put(base + "/categories/" + item._id, item) : $http.post(base + "/categories", item);
      },
      deleteCategory: function (id) { return $http.delete(base + "/categories/" + id); },
      links: function (params) { return page("/audio-links", params); },
      saveLink: function (item) {
        return item._id ? $http.put(base + "/audio-links/" + item._id, item) : $http.post(base + "/audio-links", item);
      },
      deleteLink: function (id) { return $http.delete(base + "/audio-links/" + id); },
      users: function (params) { return page("/users", params); },
      saveUser: function (item) {
        return item.id ? $http.put(base + "/users/" + item.id, item) : $http.post(base + "/users", item);
      },
      deleteUser: function (id) { return $http.delete(base + "/users/" + id); },
      credentials: function () { return $http.get(base + "/credentials"); },
      saveCredential: function (type, fields) { return $http.put(base + "/credentials/" + type, fields); },
      geoCountries: function () { return $http.get("/api/geo/countries"); },
      geoStates: function (countryCode) {
        return $http.get("/api/geo/states", { params: { countryCode: countryCode } });
      },
      geoCities: function (countryCode, state, q) {
        return $http.get("/api/geo/cities", { params: { countryCode: countryCode, state: state || "", q: q || "" } });
      },
      geoSuggest: function (countryCode, city, q) {
        return $http.get("/api/geo/suggest", { params: { countryCode: countryCode, city: city || "", q: q } });
      }
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

  app.filter("languageName", function () {
    return function (code) {
      return LANGUAGE_NAMES[code] || code || "";
    };
  });

  app.filter("stationTypeName", function () {
    return function (code) {
      return TYPE_NAMES[code] || code || "";
    };
  });

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

  app.controller("ShellCtrl", ["$scope", "$rootScope", "$location", "AuthService", "Api", "CatalogStore",
    function ($scope, $rootScope, $location, AuthService, Api, CatalogStore) {
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
        CatalogStore.clear();
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

  function bindPaged($scope, $rootScope, CatalogStore, options) {
    $scope.query = "";
    $scope.page = 0;
    $scope.size = PAGE_SIZE;
    $scope.total = 0;
    $scope.totalPages = 1;
    $scope.pageStart = 0;
    $scope.pageEnd = 0;
    $scope.items = [];
    $scope.pageLoading = false;
    $scope.refreshing = false;
    $scope.form = null;
    $scope.formError = "";
    $scope.saving = false;

    function viewKey() {
      return options.key + ":" + ($scope.filter || "") + ":" + ($scope.stationFilter || "") + ":"
        + ($scope.query || "") + ":" + $scope.page + ":" + $scope.size;
    }

    function apply(data) {
      $scope.items = (data && data.items) || [];
      $scope.total = (data && data.total) || 0;
      $scope.page = data && typeof data.page === "number" ? data.page : $scope.page;
      $scope.size = (data && data.size) || $scope.size;
      $scope.totalPages = (data && data.totalPages) || 1;
      $scope.pageStart = $scope.total ? ($scope.page * $scope.size) + 1 : 0;
      $scope.pageEnd = Math.min(($scope.page + 1) * $scope.size, $scope.total);
      CatalogStore.set(viewKey(), {
        items: $scope.items,
        total: $scope.total,
        page: $scope.page,
        size: $scope.size,
        totalPages: $scope.totalPages
      });
    }

    $scope.load = function (opts) {
      opts = opts || {};
      var cached = CatalogStore.get(viewKey());
      if (cached && cached.items && cached.items.length) {
        apply(cached);
      } else if (!opts.silent) {
        $scope.pageLoading = !$scope.items.length;
      }
      return options.fetch({
        q: $scope.query,
        page: $scope.page,
        size: $scope.size,
        status: $scope.filter,
        stationId: $scope.stationFilter
      }).then(function (res) {
        apply(res.data);
      }).catch(function (err) {
        flashError($scope, err, "Could not load data.");
      }).finally(function () {
        $scope.pageLoading = false;
      });
    };

    $scope.search = function () {
      $scope.page = 0;
      $scope.load();
    };
    $scope.prevPage = function () {
      if ($scope.page > 0) {
        $scope.page -= 1;
        $scope.load();
      }
    };
    $scope.nextPage = function () {
      if ($scope.page < $scope.totalPages - 1) {
        $scope.page += 1;
        $scope.load();
      }
    };
    $scope.changeSize = function () {
      $scope.page = 0;
      $scope.load();
    };
    $scope.refresh = function () {
      $scope.refreshing = true;
      $scope.pageLoading = !$scope.items.length;
      CatalogStore.clear();
      options.reload().then(function () {
        return $scope.load({ silent: true });
      }).then(function () {
        $rootScope.flash = { text: "Catalog reloaded from MongoDB into cache." };
      }).finally(function () {
        $scope.refreshing = false;
        $scope.pageLoading = false;
      });
    };
    $scope.closeForm = function () {
      if ($scope.saving) { return; }
      $scope.form = null;
      $scope.formError = "";
    };
    function onEscape(event) {
      if (event.keyCode === 27 && $scope.form && !$scope.saving) {
        $scope.$apply($scope.closeForm);
      }
    }
    angular.element(document).on("keydown", onEscape);
    $scope.$on("$destroy", function () {
      angular.element(document).off("keydown", onEscape);
    });
  }

  function runAction($scope, $rootScope, CatalogStore, message, work, successText) {
    $scope.saving = true;
    $scope.pageLoading = !$scope.items.length;
    return work().then(function () {
      $scope.saving = false;
      $scope.closeForm();
      CatalogStore.clear();
      return $scope.load({ silent: true });
    }).then(function () {
      $rootScope.flash = { text: successText };
    }).catch(function (err) {
      flashError($scope, err, "The request failed.");
    }).finally(function () {
      $scope.saving = false;
      $scope.pageLoading = false;
    });
  }

  function loadLookups($scope, Api, CatalogStore) {
    var cachedStations = CatalogStore.get("lookup:stations");
    var cachedCategories = CatalogStore.get("lookup:categories");
    if (cachedStations && cachedStations.items) { $scope.lookupStations = cachedStations.items; }
    if (cachedCategories && cachedCategories.items) { $scope.lookupCategories = cachedCategories.items; }
    Api.stations({ page: 0, size: 100 }).then(function (res) {
      $scope.lookupStations = res.data.items || [];
      CatalogStore.set("lookup:stations", res.data);
    }).catch(function () {});
    Api.categories({ page: 0, size: 100 }).then(function (res) {
      $scope.lookupCategories = res.data.items || [];
      CatalogStore.set("lookup:categories", res.data);
    }).catch(function () {});
  }

  app.controller("DashboardCtrl", ["$scope", "$rootScope", "Api", "CatalogStore",
    function ($scope, $rootScope, Api, CatalogStore) {
      $scope.stats = CatalogStore.get("stats") || {};
      function load() {
        $scope.pageLoading = !$scope.stats.cacheSource;
        return Api.stats().then(function (res) {
          $scope.stats = res.data;
          CatalogStore.set("stats", res.data);
        }).finally(function () {
          $scope.pageLoading = false;
        });
      }
      $scope.refresh = function () {
        $scope.pageLoading = true;
        CatalogStore.clear();
        $scope.stats = {};
        Api.reload().then(function () {
          return load();
        }).then(function () {
          $rootScope.flash = { text: "Catalog reloaded from MongoDB into cache." };
        }).finally(function () { $scope.pageLoading = false; });
      };
      load();
    }]);

  app.controller("EventsCtrl", ["$scope", "$rootScope", "$timeout", "Api", "CatalogStore",
    function ($scope, $rootScope, $timeout, Api, CatalogStore) {
      $scope.filter = "approved";
      $scope.geoCountries = [];
      $scope.geoStates = [];
      $scope.geoCities = [];
      $scope.addressSuggestions = [];
      $scope.addressSearching = false;
      var addressTimer = null;
      var suggestSeq = 0;
      Api.geoCountries().then(function (res) {
        $scope.geoCountries = res.data || [];
      }).catch(function () {});
      bindPaged($scope, $rootScope, CatalogStore, {
        key: "events",
        loadingMessage: "Loading events...",
        fetch: function (params) { return Api.events(params); },
        reload: function () { return Api.reload(); }
      });
      $scope.setFilter = function (value) {
        $scope.filter = value;
        $scope.page = 0;
        $scope.load();
      };
      $scope.openCreate = function () {
        $scope.formError = "";
        $scope.addressSuggestions = [];
        $scope.geoStates = [];
        $scope.geoCities = [];
        var start = toDatetimeLocal(new Date());
        $scope.form = {
          title: "", countryCode: "IN", country: "India", city: "", state: "", address: "",
          organizedBy: "", organization: "", description: "", status: "scheduled",
          approvalStatus: "approved", date: start, end_date: start
        };
        $scope.loadStates("IN");
      };
      $scope.openEdit = function (event) {
        $scope.formError = "";
        $scope.addressSuggestions = [];
        $scope.geoStates = [];
        $scope.geoCities = [];
        $scope.form = angular.copy(event);
        $scope.form.date = toDatetimeLocal(event.date);
        $scope.form.end_date = toDatetimeLocal(event.end_date || event.endDate || event.date);
        if ($scope.form.countryCode) {
          $scope.loadStates($scope.form.countryCode, $scope.form.state);
        }
      };
      $scope.loadStates = function (countryCode, selectedState) {
        if (!countryCode) {
          $scope.geoStates = [];
          $scope.geoCities = [];
          return;
        }
        Api.geoStates(countryCode).then(function (res) {
          $scope.geoStates = res.data || [];
          if ($scope.form && $scope.form.state && !$scope.geoStates.some(function (state) {
            return state.name === $scope.form.state;
          })) {
            $scope.geoStates.unshift({ name: $scope.form.state, code: "", countryCode: countryCode });
          }
          var stateName = selectedState || ($scope.form && $scope.form.state);
          if (stateName) {
            $scope.loadCities(countryCode, stateName);
          }
        }).catch(function () { $scope.geoStates = []; $scope.geoCities = []; });
      };
      $scope.loadCities = function (countryCode, state) {
        if (!countryCode || !state) {
          $scope.geoCities = [];
          return;
        }
        Api.geoCities(countryCode, state).then(function (res) {
          $scope.geoCities = res.data || [];
          if ($scope.form && $scope.form.city && !$scope.geoCities.some(function (city) {
            return city.name === $scope.form.city;
          })) {
            $scope.geoCities.unshift({
              name: $scope.form.city,
              state: state,
              countryCode: countryCode
            });
          }
        }).catch(function () { $scope.geoCities = []; });
      };
      $scope.onCountryChange = function () {
        var match = ($scope.geoCountries || []).filter(function (c) {
          return c.code === $scope.form.countryCode;
        })[0];
        $scope.form.country = match ? match.name : "";
        $scope.form.city = "";
        $scope.form.state = "";
        $scope.form.address = "";
        $scope.form.latitude = null;
        $scope.form.longitude = null;
        $scope.addressSuggestions = [];
        $scope.geoCities = [];
        $scope.loadStates($scope.form.countryCode);
      };
      $scope.onStateChange = function () {
        $scope.form.city = "";
        $scope.form.address = "";
        $scope.form.latitude = null;
        $scope.form.longitude = null;
        $scope.addressSuggestions = [];
        $scope.loadCities($scope.form.countryCode, $scope.form.state);
      };
      $scope.onCityChange = function () {
        $scope.form.address = "";
        $scope.form.latitude = null;
        $scope.form.longitude = null;
        $scope.addressSuggestions = [];
      };
      $scope.onAddressType = function () {
        $scope.form.latitude = null;
        $scope.form.longitude = null;
        if (addressTimer) { $timeout.cancel(addressTimer); }
        var query = ($scope.form.address || "").trim();
        if (query.length < 3 || !$scope.form.countryCode) {
          suggestSeq += 1;
          $scope.addressSuggestions = [];
          $scope.addressSearching = false;
          return;
        }
        addressTimer = $timeout(function () {
          var seq = ++suggestSeq;
          $scope.addressSearching = true;
          Api.geoSuggest($scope.form.countryCode, $scope.form.city, query).then(function (res) {
            if (seq !== suggestSeq) { return; }
            $scope.addressSuggestions = res.data || [];
            $scope.addressSearching = false;
          }).catch(function () {
            if (seq !== suggestSeq) { return; }
            $scope.addressSuggestions = [];
            $scope.addressSearching = false;
          });
        }, 400);
      };
      $scope.pickAddress = function (place) {
        $scope.form.address = place.address || place.name || $scope.form.address;
        $scope.form.latitude = place.latitude;
        $scope.form.longitude = place.longitude;
        if (place.state) { $scope.form.state = place.state; }
        if (place.name && !$scope.form.city) { $scope.form.city = place.name; }
        $scope.addressSuggestions = [];
      };
      $scope.save = function () {
        if (!$scope.form.title || !$scope.form.countryCode || !$scope.form.state || !$scope.form.city || !$scope.form.date) {
          $scope.formError = "Please fill Title, Country, State, City, and Start date.";
          return;
        }
        if ($scope.form.latitude == null || $scope.form.longitude == null) {
          $scope.formError = "Type the venue and pick an address suggestion so the map pin can be saved.";
          return;
        }
        var payload = angular.copy($scope.form);
        delete payload.$$hashKey;
        payload.date = fromDatetimeLocal(payload.date);
        payload.end_date = fromDatetimeLocal(payload.end_date) || payload.date;
        runAction($scope, $rootScope, CatalogStore, "Saving event...", function () {
          return Api.saveEvent(payload);
        }, "Event saved.");
      };
      $scope.remove = function (event) {
        if (!window.confirm("Delete this event?")) { return; }
        runAction($scope, $rootScope, CatalogStore, "Deleting event...", function () {
          return Api.deleteEvent(event._id);
        }, "Event deleted.");
      };
      $scope.approve = function (event) {
        runAction($scope, $rootScope, CatalogStore, "Approving event...", function () {
          return Api.approve(event._id, event.reviewNote);
        }, "Event approved.");
      };
      $scope.reject = function (event) {
        var note = window.prompt("Rejection note (optional)", event.reviewNote || "");
        if (note === null) { return; }
        runAction($scope, $rootScope, CatalogStore, "Rejecting event...", function () {
          return Api.reject(event._id, note);
        }, "Event rejected.");
      };
      $scope.load();
    }]);

  app.controller("StationsCtrl", ["$scope", "$rootScope", "$timeout", "Api", "CatalogStore",
    function ($scope, $rootScope, $timeout, Api, CatalogStore) {
      bindPaged($scope, $rootScope, CatalogStore, {
        key: "stations",
        loadingMessage: "Loading stations...",
        fetch: function (params) { return Api.stations(params); },
        reload: function () { return Api.reload(); }
      });
      loadLookups($scope, Api, CatalogStore);
      $scope.openCreate = function () {
        $scope.formError = "";
        $scope.form = { live: true, play_mode: "sequence", type: "radio", language: "en", nameEn: "", nameHi: "", namePa: "", category: "" };
      };
      $scope.openEdit = function (station) {
        $scope.formError = "";
        $scope.form = angular.copy(station);
        $scope.form.nameEn = (station.translations && station.translations.en && station.translations.en.name) || "";
        $scope.form.nameHi = (station.translations && station.translations.hi && station.translations.hi.name) || "";
        $scope.form.namePa = (station.translations && station.translations.pa && station.translations.pa.name) || "";
        $scope.form.play_mode = station.play_mode || station.playMode || "sequence";
        $scope.form.language = station.language || "en";
        $scope.form.type = station.type || "radio";
      };
      $scope.save = function () {
        if (!$scope.form.nameEn) {
          $scope.formError = "Please enter the English name.";
          return;
        }
        var payload = angular.copy($scope.form);
        payload.translations = {
          en: { name: payload.nameEn || "" },
          hi: { name: payload.nameHi || payload.nameEn || "" },
          pa: { name: payload.namePa || payload.nameEn || "" }
        };
        delete payload.nameEn;
        delete payload.nameHi;
        delete payload.namePa;
        runAction($scope, $rootScope, CatalogStore, "Saving station...", function () {
          return Api.saveStation(payload);
        }, "Station saved.");
      };
      $scope.remove = function (station) {
        if (!window.confirm("Delete this station and its audio links?")) { return; }
        runAction($scope, $rootScope, CatalogStore, "Deleting station...", function () {
          return Api.deleteStation(station._id);
        }, "Station deleted.");
      };
      $scope.load();
    }]);

  app.controller("CategoriesCtrl", ["$scope", "$rootScope", "$timeout", "Api", "CatalogStore",
    function ($scope, $rootScope, $timeout, Api, CatalogStore) {
      bindPaged($scope, $rootScope, CatalogStore, {
        key: "categories",
        loadingMessage: "Loading categories...",
        fetch: function (params) { return Api.categories(params); },
        reload: function () { return Api.reload(); }
      });
      $scope.iconOptions = CATEGORY_ICONS.slice();
      function withIconOption(value) {
        $scope.iconOptions = CATEGORY_ICONS.slice();
        if (value && !$scope.iconOptions.some(function (icon) { return icon.value === value; })) {
          $scope.iconOptions.unshift({ value: value, label: value });
        }
      }
      $scope.openCreate = function () {
        $scope.formError = "";
        withIconOption("music_note");
        $scope.form = { order: 1, icon: "music_note", nameEn: "", nameHi: "", namePa: "", category: "" };
      };
      $scope.openEdit = function (category) {
        $scope.formError = "";
        $scope.form = angular.copy(category);
        $scope.form.nameEn = (category.translations && category.translations.en && category.translations.en.name) || "";
        $scope.form.nameHi = (category.translations && category.translations.hi && category.translations.hi.name) || "";
        $scope.form.namePa = (category.translations && category.translations.pa && category.translations.pa.name) || "";
        withIconOption($scope.form.icon);
      };
      $scope.save = function () {
        if (!$scope.form.category || !$scope.form.nameEn) {
          $scope.formError = "Please fill Key and English name.";
          return;
        }
        var payload = angular.copy($scope.form);
        payload.translations = {
          en: { name: payload.nameEn || payload.category },
          hi: { name: payload.nameHi || payload.nameEn || payload.category },
          pa: { name: payload.namePa || payload.nameEn || payload.category }
        };
        delete payload.nameEn;
        delete payload.nameHi;
        delete payload.namePa;
        runAction($scope, $rootScope, CatalogStore, "Saving category...", function () {
          return Api.saveCategory(payload);
        }, "Category saved.");
      };
      $scope.remove = function (category) {
        if (!window.confirm("Delete this category?")) { return; }
        runAction($scope, $rootScope, CatalogStore, "Deleting category...", function () {
          return Api.deleteCategory(category._id);
        }, "Category deleted.");
      };
      $scope.load();
    }]);

  app.controller("LinksCtrl", ["$scope", "$rootScope", "$timeout", "Api", "CatalogStore",
    function ($scope, $rootScope, $timeout, Api, CatalogStore) {
      $scope.stationFilter = "";
      bindPaged($scope, $rootScope, CatalogStore, {
        key: "links",
        loadingMessage: "Loading audio links...",
        fetch: function (params) { return Api.links(params); },
        reload: function () { return Api.reload(); }
      });
      loadLookups($scope, Api, CatalogStore);
      $scope.stationLabel = function (id) {
        var match = ($scope.lookupStations || []).filter(function (station) {
          return station._id === id;
        })[0];
        if (!match) { return id || ""; }
        var tr = match.translations || {};
        return (tr.en && tr.en.name) || match._id;
      };
      $scope.setStationFilter = function () {
        $scope.page = 0;
        $scope.load();
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
        runAction($scope, $rootScope, CatalogStore, "Saving audio link...", function () {
          return Api.saveLink(payload);
        }, "Audio link saved.");
      };
      $scope.remove = function (link) {
        if (!window.confirm("Delete this audio link?")) { return; }
        runAction($scope, $rootScope, CatalogStore, "Deleting audio link...", function () {
          return Api.deleteLink(link._id);
        }, "Audio link deleted.");
      };
      $scope.load();
    }]);

  app.controller("UsersCtrl", ["$scope", "$rootScope", "$timeout", "Api", "CatalogStore",
    function ($scope, $rootScope, $timeout, Api, CatalogStore) {
      $scope.modules = [
        { key: "events", label: "Events", approve: true },
        { key: "stations", label: "Stations" },
        { key: "categories", label: "Categories" },
        { key: "audioLinks", label: "Audio links" },
        { key: "users", label: "Sub-admins" }
      ];
      bindPaged($scope, $rootScope, CatalogStore, {
        key: "users",
        loadingMessage: "Loading users...",
        fetch: function (params) { return Api.users(params); },
        reload: function () { return Api.reload(); }
      });
      loadLookups($scope, Api, CatalogStore);
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
        runAction($scope, $rootScope, CatalogStore, "Saving user...", function () {
          return Api.saveUser(payload);
        }, "User saved.");
      };
      $scope.remove = function (user) {
        if (!window.confirm("Delete sub-admin " + user.username + "?")) { return; }
        runAction($scope, $rootScope, CatalogStore, "Deleting user...", function () {
          return Api.deleteUser(user.id);
        }, "User deleted.");
      };
      $scope.load();
    }]);

  app.controller("SettingsCtrl", ["$scope", "Api",
    function ($scope, Api) {
      var labels = {
        host: "SMTP Host",
        port: "SMTP Port",
        from: "From Address",
        auth: "SMTP Auth",
        starttls: "STARTTLS",
        bucket: "Bucket Name",
        prefix: "Prefix",
        region: "Region",
        endpointUrl: "Endpoint URL",
        applicationKeyId: "Application Key ID",
        applicationKey: "Application Key",
        provider: "Geo Provider",
        apiKey: "Geo API Key",
        cluster: "Cluster Host",
        database: "Database Name",
        srv: "SRV connection"
      };
      var typeLabels = {
        GMAIL: { username: "Gmail Username", password: "App Password" },
        MONGO: { username: "Mongo Username", password: "Mongo Password" },
        MYSQL: { username: "MySQL Username", password: "MySQL Password", host: "MySQL Host", port: "MySQL Port", database: "Database Name", useSsl: "Use SSL" }
      };
      var placeholders = {
        GMAIL: {
          host: "e.g. smtp.gmail.com",
          port: "e.g. 587",
          username: "e.g. vikasbeersingh@gmail.com",
          from: "e.g. vikasbeersingh@gmail.com",
          password: "Leave blank to keep the saved password",
          auth: "e.g. true",
          starttls: "e.g. true"
        },
        B2: {
          bucket: "e.g. OCRPunjabiData",
          prefix: "e.g. paddle_dataset",
          region: "e.g. us-east-005",
          endpointUrl: "e.g. https://s3.us-east-005.backblazeb2.com",
          applicationKeyId: "e.g. 41a1bdb99cac",
          applicationKey: "Leave blank to keep the saved key"
        },
        GEO: {
          provider: "photon (default, no key). Optional: locationiq, geoapify, countrystatecity",
          apiKey: "Optional. Photon needs no key. Paste LocationIQ/Geoapify/CSC key here"
        },
        MONGO: {
          username: "e.g. atlasUser",
          password: "Leave blank to keep the saved password",
          cluster: "e.g. cluster0.abc123.mongodb.net",
          database: "e.g. divine_bliss_streaming",
          srv: "true"
        },
        MYSQL: {
          host: "e.g. 129.225.124.207",
          port: "e.g. 3306",
          username: "MySQL username for bani_search",
          password: "Leave blank to keep the saved password",
          database: "bani_search",
          useSsl: "false"
        }
      };
      $scope.items = [];
      $scope.pageLoading = true;
      $scope.saving = false;
      $scope.formError = "";
      $scope.fieldLabel = function (type, key) {
        return (typeLabels[type] && typeLabels[type][key]) || labels[key] || key;
      };
      $scope.fieldPlaceholder = function (type, key) {
        return (placeholders[type] && placeholders[type][key]) || "";
      };
      $scope.isSecret = function (key) {
        return /password|applicationKey|secret|apiKey/i.test(key || "");
      };
      $scope.load = function () {
        $scope.pageLoading = !$scope.items.length;
        Api.credentials().then(function (res) {
          $scope.items = res.data || [];
        }).catch(function (err) {
          flashError($scope, err, "Could not load credentials.");
        }).finally(function () {
          $scope.pageLoading = false;
        });
      };
      $scope.save = function (item) {
        $scope.saving = true;
        $scope.formError = "";
        Api.saveCredential(item.type, item.fields).then(function (res) {
          item.fields = res.data.fields;
          $scope.$root.flash = { text: item.type + " credentials saved." };
        }).catch(function (err) {
          flashError($scope, err, "Could not save credentials.");
        }).finally(function () {
          $scope.saving = false;
        });
      };
      $scope.load();
    }]);
})();
