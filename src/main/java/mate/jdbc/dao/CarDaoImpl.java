package mate.jdbc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mate.jdbc.exception.DataProcessingException;
import mate.jdbc.lib.Dao;
import mate.jdbc.lib.Inject;
import mate.jdbc.model.Car;
import mate.jdbc.model.Driver;
import mate.jdbc.util.ConnectionUtil;

@Dao
public class CarDaoImpl implements CarDao {
    @Inject
    private ManufacturerDao manufacturerDao;

    @Override
    public Car create(Car car) {
        String query = "INSERT INTO cars (model, manufacturer_id) VALUES (?, ?);";
        try (Connection connection = ConnectionUtil.getConnection();
                   PreparedStatement preparedStatement = connection.prepareStatement(query,
                        Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setString(1, car.getModel());
            preparedStatement.setLong(2, car.getManufacturer().getId());
            preparedStatement.executeUpdate();
            ResultSet resultSet = preparedStatement.getGeneratedKeys();
            if (resultSet.next()) {
                car.setId(resultSet.getObject(1, Long.class));
            }
        } catch (SQLException e) {
            throw new DataProcessingException("Can`t create car: " + car + " in database", e);
        }
        insertDrivers(car);
        return car;
    }

    @Override
    public Optional<Car> get(Long id) {
        String query = "SELECT id, model, manufacturer_id FROM cars WHERE id = ? "
                + "AND is_deleted = FALSE;";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setLong(1, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            Car car = null;
            if (resultSet.next()) {
                car = getCar(resultSet);
            }
            if (car != null) {
                car.setDrivers(setDriversList(id));
            }
            return Optional.ofNullable(car);
        } catch (SQLException e) {
            throw new DataProcessingException("Can`t get car from database with id: " + id, e);
        }
    }

    @Override
    public List<Car> getAll() {
        List<Car> list = new ArrayList<>();
        String query = "SELECT id, model, manufacturer_id FROM cars WHERE is_deleted = FALSE;";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                list.add(getCar(resultSet));
            }
            for (Car car : list) {
                car.setDrivers(setDriversList(car.getId()));
            }
            return list;
        } catch (SQLException e) {
            throw new DataProcessingException("Can`t get all cars from database", e);
        }
    }

    @Override
    public Car update(Car car) {
        String query = "UPDATE cars SET model = ?, manufacturer_id = ? "
                + "WHERE id = ? AND is_deleted = FALSE;";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, car.getModel());
            preparedStatement.setLong(2, car.getManufacturer().getId());
            preparedStatement.setLong(3, car.getId());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new DataProcessingException("Can`t update car in database", e);
        }
        insertDrivers(car);
        deleteDriversFromCar(car);
        return car;
    }

    @Override
    public boolean delete(Long id) {
        String query = "UPDATE cars SET is_deleted = FALSE "
                + "WHERE id = ?;";
        try (Connection connection = ConnectionUtil.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setLong(1, id);
            return preparedStatement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataProcessingException("Can`t delete car in database", e);
        }
    }

    @Override
    public List<Car> getAllByDriver(Long driverId) {
        List<Car> carList = new ArrayList<>();
        String query = "SELECT model, manufacturer_id FROM cars c JOIN cars_drivers cd "
                + "ON c.id = cd.car_id WHERE cd.driver_id = ?;";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setLong(1, driverId);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                carList.add(getCar(resultSet));
            }
            for (Car car : carList) {
                car.setDrivers(getDriversOfCar(car.getId()));
            }
            return carList;
        } catch (SQLException e) {
            throw new DataProcessingException("Can`t get car from database with driver id: "
                    + driverId, e);
        }
    }

    private List<Driver> getDriversOfCar(Long carId) {
        List<Driver> driverList = new ArrayList<>();
        String query = "SELECT * FROM drivers d JOIN cars_drivers cd "
                + "ON d.id = cd.driver_id WHERE cd.car_id = ?;";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setLong(1, carId);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                driverList.add(getDrivers(resultSet));
            }
            return driverList;
        } catch (SQLException e) {
            throw new DataProcessingException("Can`t get drivers from database with car id: "
                    + carId, e);
        }
    }

    private void insertDrivers(Car car) {
        String query = "INSERT INTO cars_drivers (car_id, driver_id) "
                + "VALUES (?, ?);";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setLong(1, car.getId());
            if (car.getDrivers() != null) {
                for (Driver driver : car.getDrivers()) {
                    preparedStatement.setLong(2, driver.getId());
                    preparedStatement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new DataProcessingException("Can`t add data in table cars_drivers", e);
        }
    }

    private List<Driver> setDriversList(Long carId) {
        List<Driver> list = new ArrayList<>();
        String query = "SELECT id, name, license_number FROM drivers d "
                + "JOIN cars_drivers cd ON d.id = cd.driver_id "
                   + "WHERE cd.driver_id = ? AND is_deleted = FALSE;";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setLong(1, carId);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                list.add(getDrivers(resultSet));
            }
        } catch (SQLException e) {
            throw new DataProcessingException("Cant set driver list for car with id: " + carId, e);
        }
        return list;
    }

    private void deleteDriversFromCar(Car car) {
        String query = "DELETE FROM cars_drivers "
                + "WHERE cars_drivers.car_id = ?;";
        try (Connection connection = ConnectionUtil.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setLong(1, car.getId());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new DataProcessingException("Can`t delete drivers for car with id: "
                    + car.getId(), e);
        }
    }

    private Driver getDrivers(ResultSet resultSet) throws SQLException {
        Long id = resultSet.getLong(1);
        String name = resultSet.getString(2);
        String licenseNumber = resultSet.getString(3);
        return new Driver(id, name, licenseNumber);
    }

    private Car getCar(ResultSet resultSet) throws SQLException {
        Long id = resultSet.getLong(1);
        String model = resultSet.getString(2);
        Long manufacturerId = resultSet.getLong(3);
        return new Car(id, model, manufacturerDao.get(manufacturerId).get());
    }
}
