$scope.user = { storageQuota: 100 }; // 默认100MB

$scope.register = function() {
    $scope.loading = true;
    $http({
        method: 'POST',
        url: 'api/user/register',
        data: $.param({
            username: $scope.user.username,
            password: $scope.user.password,
            email: $scope.user.email,
            storage_quota: $scope.user.storageQuota * 1024 * 1024
        }),
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    }).then(function(response) {
        $scope.loading = false;
        $scope.message = response.data.message;
        $scope.status = 'pending';
        
        // Start polling for status
        pollRegistrationStatus();
    }, function(response) {
        $scope.loading = false;
        $scope.error = '注册失败: ' + (response.data && response.data.message ? response.data.message : '未知错误');
    });
};

function pollRegistrationStatus() {
    if ($scope.status === 'pending') {
        $http.get('api/user/register/status/' + $scope.user.username).then(function(response) {
            $scope.status = response.data.status;
            $scope.message = response.data.message;
            
            if (response.data.status === 'PENDING') {
                // Continue polling
                setTimeout(pollRegistrationStatus, 5000);
            } else if (response.data.status === 'APPROVED') {
                // Redirect to login page after 3 seconds
                setTimeout(function() {
                    $state.go('login');
                }, 3000);
            }
        });
    }
} 