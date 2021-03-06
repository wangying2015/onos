/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

(function () {
    'use strict';

    var url = 'http://localhost:8080/rs/dashboard';

    angular.module('cordHome', [])
        .controller('CordHomeCtrl', ['$log', '$scope', '$resource',
            function ($log, $scope, $resource) {
                var DashboardData, resource;
                $scope.page = 'dashboard';

                DashboardData = $resource(url);
                resource = DashboardData.get({},
                    // success
                    function () {
                        $scope.bundle = resource.bundle;
                        $scope.users = resource.users;
                    },
                    // error
                    function () {
                        $log.error('Problem with resource', resource);
                    });
                $log.debug('Resource received:', resource);

                $log.debug('Cord Home Ctrl has been created.');
        }]);
}());
